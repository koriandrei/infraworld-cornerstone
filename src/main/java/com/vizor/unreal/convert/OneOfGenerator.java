package com.vizor.unreal.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vizor.unreal.tree.CppArgument;
import com.vizor.unreal.tree.CppClass;
import com.vizor.unreal.tree.CppEnum;
import com.vizor.unreal.tree.CppField;
import com.vizor.unreal.tree.CppFunction;
import com.vizor.unreal.tree.CppStruct;
import com.vizor.unreal.tree.CppType;
import com.vizor.unreal.tree.CppType.Kind;
import com.vizor.unreal.tree.CppType.Passage;
import com.vizor.unreal.util.Tuple;

class OneOfGenerator 
{

    class OneOfTemp
    {
        public OneOfTemp(CppStruct first, CppStruct second, final Tuple<CppStruct, CppEnum>... oneOfGeneratedStructs) {
            protoMessage = first;
            ueMessage = second;
            this.oneOfGeneratedStructs = Arrays.asList(oneOfGeneratedStructs);
        }

        CppStruct ueMessage;
        CppStruct protoMessage;
        List<Tuple<CppStruct,CppEnum>> oneOfGeneratedStructs;
    }

    List<OneOfTemp> oneOfs;

	public OneOfGenerator(List<Tuple<Tuple<CppStruct, CppStruct>, Tuple<CppStruct, CppEnum>>> oneOfs) {
        this.oneOfs = oneOfs.stream().map((Tuple<Tuple<CppStruct, CppStruct>, Tuple<CppStruct, CppEnum>> tuple)->
            new OneOfTemp(tuple.first().first(), tuple.first().second(), tuple.second())
        ).collect(Collectors.toList());
	}

    private static CppFunction generateTryGet()
    {
        final CppType templateType = CppType.plain("T", Kind.Wildcard);

        final List<CppType> templateArgs = new ArrayList<>();
        templateArgs.add(templateType);
        
        final CppArgument outValueArgument = new CppArgument(
            templateType.makeRef(), "OutValue");

        final List<CppArgument> arguments = new ArrayList<>();
        arguments.add(outValueArgument);
        
        final CppFunction templatedGetOneOf = new CppFunction(
            "TryGetValue", 
            CppType.plain("bool", Kind.Primitive), 
            new ArrayList<>(), 
            templateArgs
            );
        
        templatedGetOneOf.setBody("return false;");

        templatedGetOneOf.enableAnnotations(false);

        return templatedGetOneOf;
    }

    private static CppFunction generateCreateFunction(final CppType oneOfType, final CppEnum caseEnumType, final int CaseEnumValue, final CppType typeToGenerateFor)
    {
        List<CppArgument> arguments = new ArrayList<>();

        arguments.add(
            new CppArgument(
                typeToGenerateFor.makeConstant(Passage.ByRef), 
                "OneOfValue"
            )
        );

        CppFunction createFunction = new CppFunction(
            "Create"+typeToGenerateFor.getName(), 
            oneOfType, 
            arguments
        );

        createFunction.isStatic = true;

        createFunction.setBody(
            String.join(System.lineSeparator()
                , oneOfType.getName() + " OneOf;"
                , "OneOf.Set(OneOfValue);"
                , "return OneOf;"
            )
        );

        return createFunction;
    }

    private static CppArgument makeRef(final CppArgument argument, final boolean constant)
    {
        return new CppArgument(argument.getType().makeRef(constant, false), argument.getName());
    }

    private static CppClass generateOneOfCasts(final OneOfTemp oneOfDeclaration)
    {
        final CppType castHelperClass = CppType.wildcardGeneric("TOneOfHelpers", Kind.Struct, 2);

        final CppType castHelperSpecialized = castHelperClass.makeGeneric(oneOfDeclaration.ueMessage.getType(), oneOfDeclaration.protoMessage.getType());

        final CppArgument protoArgument = new CppArgument(oneOfDeclaration.protoMessage.getType(), "ProtoMessage");
        
        final CppArgument ueArgument = new CppArgument(oneOfDeclaration.ueMessage.getType(), "UnrealMessage");

        final CppFunction protoToUnreal = new CppFunction(
            "LoadFromProto", 
            CppType.plain("void", Kind.Primitive), 
            Arrays.asList(makeRef(protoArgument, true), makeRef(ueArgument, false))
        );

        final CppFunction unrealToProto = new CppFunction(
            "SaveToProto", 
            CppType.plain("void", Kind.Primitive), 
            Arrays.asList(makeRef(ueArgument, true), makeRef(protoArgument, false))
        );

        final CppClass Specialization = new CppClass(
            castHelperSpecialized, 
            /*superType =*/ null, 
            new ArrayList<>(), 
            Arrays.asList(protoToUnreal, unrealToProto)
        );

        return Specialization;
    }

    private static Stream<CppClass> generateOneOfImpl(final OneOfTemp oneOfDeclaration)
    {
        CppType type = CppType.plain(oneOfDeclaration.ueMessage.getType().getName() + "OneOf" + "Helpers", Kind.Class);
        
        List<CppField> fields = new ArrayList<>();

        final List<CppFunction> methods = new ArrayList<>();

        methods.add(generateTryGet());

        methods.addAll(
            oneOfDeclaration.oneOfGeneratedStructs.stream()
            .flatMap((x)->{return x.first().getFields().stream().map(
            (final CppField field) -> {
                return generateCreateFunction(
                    x.first().getType(),
                    x.second(), 
                    1,
                    field.getType()
                    );
            });
            }).collect(Collectors.toList()));

        return Arrays.asList(
            new CppClass(type, null, fields, methods),
            generateOneOfCasts(oneOfDeclaration)
        ).stream();
    }

    public List<CppClass> genOneOfs() 
    {
        return oneOfs
            .stream()
            .flatMap(OneOfGenerator::generateOneOfImpl)
            .collect(Collectors.toList()
        );
        
	}

}
