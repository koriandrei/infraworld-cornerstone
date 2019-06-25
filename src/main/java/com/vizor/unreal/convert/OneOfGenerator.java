package com.vizor.unreal.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vizor.unreal.tree.CppArgument;
import com.vizor.unreal.tree.CppClass;
import com.vizor.unreal.tree.CppEnum;
import com.vizor.unreal.tree.CppField;
import com.vizor.unreal.tree.CppFunction;
import com.vizor.unreal.tree.CppStruct;
import com.vizor.unreal.tree.CppType;
import com.vizor.unreal.tree.CppType.Kind;
import com.vizor.unreal.util.Tuple;

class OneOfGenerator 
{

    class OneOfTemp
    {
        public OneOfTemp(CppStruct first, CppStruct second, CppStruct first2, CppEnum second2) {
            ueMessage = first;
            protoMessage = second;
            oneOfGeneratedStruct = first2;
            oneOfGeneratedCaseEnum = second2;
        }

        CppStruct ueMessage;
        CppStruct protoMessage;
        CppStruct oneOfGeneratedStruct;
        CppEnum oneOfGeneratedCaseEnum;
    }

    List<OneOfTemp> oneOfs;

	public OneOfGenerator(List<Tuple<Tuple<CppStruct, CppStruct>, Tuple<CppStruct, CppEnum>>> oneOfs) {
        this.oneOfs = oneOfs.stream().map((Tuple<Tuple<CppStruct, CppStruct>, Tuple<CppStruct, CppEnum>> tuple)->
            new OneOfTemp(tuple.first().first(), tuple.first().second(), tuple.second().first(), tuple.second().second())
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

    private static CppClass generateOneOfImpl(final OneOfTemp oneOfDeclaration)
    {
        CppType type = CppType.plain(oneOfDeclaration.ueMessage.getType().getName() + "_access", Kind.Class);
        
        List<CppField> fields = new ArrayList<>();

        fields.add(new CppField(oneOfDeclaration.oneOfGeneratedCaseEnum.getType(), "oneofcase"));

        final List<CppFunction> methods = new ArrayList<>();

        methods.add(generateTryGet());

        return new CppClass(type, null, fields, methods);
    }

    public List<CppClass> genOneOfs() 
    {
        return oneOfs
            .stream()
            .map(OneOfGenerator::generateOneOfImpl)
            .collect(Collectors.toList()
        );
        
	}

}
