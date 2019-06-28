package com.vizor.unreal.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.vizor.unreal.provider.TypesProvider;
import com.vizor.unreal.tree.CppArgument;
import com.vizor.unreal.tree.CppClass;
import com.vizor.unreal.tree.CppEnum;
import com.vizor.unreal.tree.CppField;
import com.vizor.unreal.tree.CppFunction;
import com.vizor.unreal.tree.CppRecord;
import com.vizor.unreal.tree.CppStruct;
import com.vizor.unreal.tree.CppType;
import com.vizor.unreal.tree.CppType.Kind;
import com.vizor.unreal.tree.CppType.Passage;
import com.vizor.unreal.util.Tuple;

class OneOfDefinition {
    public OneOfDefinition(List<OneOfInStruct> oneOfStructs, CppStruct messageUnrealStruct,
            CppStruct messageProtoStruct, MessageElement ownerMessageElement) {
        this.oneOfStructs = oneOfStructs;
        this.messageUnrealStruct = messageUnrealStruct;
        this.messageProtoStruct = messageProtoStruct;
        this.ownerMessageElement = ownerMessageElement;
    }

    public List<OneOfInStruct> oneOfStructs;
    public CppStruct messageUnrealStruct;
    public CppStruct messageProtoStruct;
    public MessageElement ownerMessageElement;
}

class OneOfInStruct {
    public OneOfInStruct(CppEnum oneOfCaseEnum, List<CppField> fields, CppStruct oneOfUnrealStruct,
            OneOfElement oneOfElement) {
        this.oneOfCaseEnum = oneOfCaseEnum;
        this.fields = fields;
        this.oneOfUnrealStruct = oneOfUnrealStruct;
        this.oneOfElement = oneOfElement;
    }

    public CppEnum oneOfCaseEnum;
    public List<CppField> fields;
    public CppStruct oneOfUnrealStruct;
    public OneOfElement oneOfElement;
}

class OneOfGenerator {
    private List<OneOfDefinition> oneOfs;
    private TypesProvider ueProvider;
    private TypesProvider protoProvider;

    public OneOfGenerator(TypesProvider ueProvider, TypesProvider protoProvider, List<OneOfDefinition> oneOfs) {
        this.ueProvider = ueProvider;
        this.protoProvider = protoProvider;
        this.oneOfs = oneOfs;
    }

    private static CppFunction generateTryGet() {
        final CppType templateType = CppType.plain("T", Kind.Wildcard);

        final List<CppType> templateArgs = new ArrayList<>();
        templateArgs.add(templateType);

        final CppArgument outValueArgument = new CppArgument(templateType.makeRef(), "OutValue");

        final List<CppArgument> arguments = new ArrayList<>();
        arguments.add(outValueArgument);

        final CppFunction templatedGetOneOf = new CppFunction("TryGetValue", CppType.plain("bool", Kind.Primitive),
                new ArrayList<>(), templateArgs);

        templatedGetOneOf.setBody("return false;");

        templatedGetOneOf.enableAnnotations(false);

        return templatedGetOneOf;
    }

    private static CppFunction generateCreateFunction(final CppType oneOfType, final CppEnum caseEnumType,
            final int CaseEnumValue, final CppType typeToGenerateFor) {
        List<CppArgument> arguments = new ArrayList<>();

        arguments.add(new CppArgument(typeToGenerateFor.makeConstant(Passage.ByRef), "OneOfValue"));

        CppFunction createFunction = new CppFunction(
                "Create" + oneOfType.getName() + "From" + typeToGenerateFor.getName(), oneOfType, arguments);

        createFunction.isStatic = true;

        createFunction.setBody(String.join(System.lineSeparator(), oneOfType.getName() + " OneOf;",
                "OneOf.Set(OneOfValue);", "return OneOf;"));

        return createFunction;
    }

    private static CppArgument makeRef(final CppArgument argument, final boolean constant) {
        return new CppArgument(argument.getType().makeRef(constant, false), argument.getName());
    }

    private static CppClass generateOneOfCasts(final OneOfDefinition oneOfDefinition) {
        final CppType castHelperClass = CppType.wildcardGeneric("TOneOfHelpers", Kind.Struct, 2);

        final CppType castHelperSpecialized = castHelperClass.makeGeneric(oneOfDefinition.messageUnrealStruct.getType(),
                oneOfDefinition.messageProtoStruct.getType());
                

        final CppArgument protoArgument = new CppArgument(oneOfDefinition.messageProtoStruct.getType(), "ProtoMessage");

        final CppArgument ueArgument = new CppArgument(oneOfDefinition.messageUnrealStruct.getType(), "UnrealMessage");

        final CppFunction protoToUnreal = new CppFunction("LoadFromProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(makeRef(protoArgument, true), makeRef(ueArgument, false)));

        OneOfInStruct currentOneOf = oneOfDefinition.oneOfStructs.get(0);

        CppEnum caseValues = generateOneOfEnum(currentOneOf);

        String protoCaseEnumName = caseValues.getType().getName();
        String protoCaseEnumGetMethodName = caseValues.getType().getName();

        // TODO: this would have another name
        String ueCaseEnumName = caseValues.getType().getName();

        CppStruct oneOfStruct = currentOneOf.oneOfUnrealStruct;

        protoToUnreal.setBody(String.join(System.lineSeparator(),
                protoCaseEnumName + " ProtoCase = ProtoMessage." + protoCaseEnumGetMethodName + "();",
                ueCaseEnumName + " UeCase = static_cast<uint8>(ProtoCase);",
                oneOfStruct.getType().getName() + " UeOneOf;", "switch (ProtoCase)", "{",
                oneOfStruct.getFields().stream().map((field) -> {
                    String protoGetMethodName = field.getName();

                    String ueSetMethodName = "Set" + field.getName();

                    return String.join(System.lineSeparator(), "case " + field.getName() + ":",
                            "\tUeOneOf." + ueSetMethodName + "(ProtoCast<" + field.getType().getName()
                                    + ">(ProtoMessage." + protoGetMethodName + "());",
                            "\tbreak;");
                }).collect(Collectors.joining()), "}"));

        final CppFunction unrealToProto = new CppFunction("SaveToProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(makeRef(ueArgument, true), makeRef(protoArgument, false)));

        final CppClass Specialization = new CppClass(castHelperSpecialized, /* superType = */ null, new ArrayList<>(),
                Arrays.asList(protoToUnreal, unrealToProto));

        return Specialization;
    }

    private CppClass generateHelpersClass(final OneOfDefinition oneOfDefinition) {
        CppType type = CppType.plain(oneOfDefinition.messageUnrealStruct.getType().getName() + "OneOfHelpers",
                Kind.Class);

        List<CppField> fields = new ArrayList<>();

        final List<CppFunction> methods = new ArrayList<>();

        methods.add(generateTryGet());

        methods.addAll(oneOfDefinition.oneOfStructs.stream().flatMap((oneOfStruct) -> {
            return (oneOfStruct.oneOfElement.fields().stream().map((oneOfField) -> {
                return generateCreateFunction(
                    oneOfStruct.oneOfUnrealStruct.getType(),
                    generateOneOfEnum(oneOfStruct), 
                    oneOfField.tag(), 
                    getOneOfFieldType(oneOfField)
                );
            })

            );
        }).collect(Collectors.toList()));

        return new CppClass(type, null, fields, methods);

    }

    private CppType getOneOfFieldType(FieldElement oneOfField) {
        return ProtoProcessor.ParseField(ueProvider, oneOfField).getType();
    }

    private static CppEnum generateOneOfEnum(OneOfInStruct currentOneOf) {
        return currentOneOf.oneOfCaseEnum;
    }

    private Collection<CppRecord> generateOneOfImpl(final OneOfDefinition oneOfDefinition)
    {

        return Arrays.asList(
            generateHelpersClass(oneOfDefinition),
            generateOneOfCasts(oneOfDefinition)
        );
    }

    public Collection<CppRecord> genOneOfs() 
    {
        return oneOfs
            .stream()
            .flatMap((x) -> {return generateOneOfImpl(x).stream();})
            .collect(Collectors.toList()
        );
        
	}

}
