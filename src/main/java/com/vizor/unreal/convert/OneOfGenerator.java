package com.vizor.unreal.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.vizor.unreal.provider.TypesProvider;
import com.vizor.unreal.tree.CppAnnotation;
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

    private CppFunction generateProtoToUeCast(final OneOfDefinition oneOfDefinition) {
        final CppArgument protoArgument = new CppArgument(oneOfDefinition.messageProtoStruct.getType(), "ProtoMessage");

        final CppArgument ueArgument = new CppArgument(oneOfDefinition.messageUnrealStruct.getType(), "UnrealMessage");

        final CppFunction protoToUnreal = new CppFunction("LoadFromProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(makeRef(protoArgument, true), makeRef(ueArgument, false)));

        final OneOfInStruct currentOneOf = oneOfDefinition.oneOfStructs.get(0);

        CppEnum caseValues = generateOneOfEnum(currentOneOf);

        String protoCaseEnumName = caseValues.getType().getName();
        String protoCaseEnumGetMethodName = caseValues.getType().getName();

        CppStruct oneOfStruct = currentOneOf.oneOfUnrealStruct;

        protoToUnreal.setBody(String.join(System.lineSeparator(),
                protoCaseEnumName + " ProtoCase = ProtoMessage." + protoCaseEnumGetMethodName + "();",
                oneOfStruct.getType().getName() + " UeOneOf;", "switch (ProtoCase)", "{",

                currentOneOf.oneOfElement.fields().stream().map((fieldElement) -> {
                    return String.join(System.lineSeparator(), "\tcase " + fieldElement.tag() + ":", "\t{",
                            "\t\tTValueBox<" + ueProvider.get(fieldElement.type()) + "> OutItem;",
                            "\t\t" + CastGenerator.generateProtoToUeCast(
                                    ProtoProcessor.ParseField(protoProvider, fieldElement),
                                    new CppField(ueProvider.get(fieldElement.type()), "Value")),
                            "\t\tUnrealMessage.Set(OutItem.GetValue(), " + fieldElement.tag() + ");", "\t\tbreak;",
                            "\t}");
                }).collect(Collectors.joining(System.lineSeparator())), "}"));

        return protoToUnreal;
    }

    private void generateCastBody(OneOfDefinition oneOfDefinition, CppFunction castFunction, boolean isProtoToUeCast) {
        String body = oneOfDefinition.oneOfStructs.stream()
                .map((oneOfStruct) -> generateCastFor(oneOfDefinition, oneOfStruct, isProtoToUeCast))
                .collect(Collectors.joining(System.lineSeparator()));

        castFunction.setBody(body);
    }

    private String generateCastFor(OneOfDefinition oneOfDefinition, OneOfInStruct currentOneOf,
            boolean isProtoToUeCast) {
        CppEnum caseValues = generateOneOfEnum(currentOneOf);

        String protoCaseEnumName = caseValues.getType().getName();
        String protoCaseEnumGetMethodName = caseValues.getType().getName();

        CppStruct oneOfStruct = currentOneOf.oneOfUnrealStruct;

        if (isProtoToUeCast) {
            return String.join(System.lineSeparator(),
                    "{",
                    "\t" + protoCaseEnumName + " ProtoCase = ProtoMessage." + protoCaseEnumGetMethodName + "();",
                    "\t" + oneOfStruct.getType().getName() + " UeOneOf;", "switch (ProtoCase)", "{",

                    currentOneOf.oneOfElement.fields().stream().map((fieldElement) -> {
                        return String.join(System.lineSeparator(), 
                            "\t\tcase " + fieldElement.tag() + ":", 
                            "\t\t{",
                            "\t\t\tTValueBox<" + ueProvider.get(fieldElement.type()) + "> OutItem;",
                            "\t\t\t" + CastGenerator.generateProtoToUeCast(
                                        ProtoProcessor.ParseField(protoProvider, fieldElement),
                                        new CppField(ueProvider.get(fieldElement.type()), "Value")
                                    ),
                            "\t\t\tUeOneOf.Set(OutItem.GetValue(), " + fieldElement.tag() + ");", 
                            "\t\t\tbreak;",
                            "\t\t}");
                    }).collect(Collectors.joining(System.lineSeparator())),
                    "\t}", 
                    "UnrealMessage." + currentOneOf.oneOfElement.name() + " = UeOneOf;",
                    "}");
        }
        else
        {
            return String.join(System.lineSeparator()
            , "{"
            , "\t" + oneOfStruct.getType().makeRef(true, false).getName() + " UeOneOf = UnrealMessage." + currentOneOf.oneOfElement.name() + ";"
            , "\tswitch (UeOneOf.GetCurrentTypeId())"
            , "\t{"
            , currentOneOf.oneOfElement.fields().stream().map((fieldElement)->
                String.join(
                    System.lineSeparator()
                    , "\t\tcase " + fieldElement.tag() + ":"
                    , "\t\t{"
                    , "\t\t\tTValueBox<" + ueProvider.get(fieldElement.type()) + "> Item;"
                    , "\t\t\tensure(UeOneOf.TryGet(Item.Value, " + fieldElement.tag() +  ");"
                    , "\t\t\t" + CastGenerator.generateUeToProtoCast(
                            new CppField(ueProvider.get(fieldElement.type()), "Value"), 
                            ProtoProcessor.ParseField(protoProvider, fieldElement)
                        )
                    , "\t\t\tbreak;"
                    , "\t\t}"
                )   
            ).collect(Collectors.joining(System.lineSeparator()))
            , "\t}"
            , "}"
            );
        }
    }

    private CppClass generateOneOfCasts(final OneOfDefinition oneOfDefinition) {
        final CppType castHelperClass = CppType.wildcardGeneric("TOneOfHelpers", Kind.Struct, 2);

        final CppType castHelperSpecialized = castHelperClass.makeGeneric(oneOfDefinition.messageUnrealStruct.getType(),
                oneOfDefinition.messageProtoStruct.getType());

        final CppArgument protoArgument = new CppArgument(oneOfDefinition.messageProtoStruct.getType(), "ProtoMessage");

        final CppArgument ueArgument = new CppArgument(oneOfDefinition.messageUnrealStruct.getType(), "UnrealMessage");

        final CppFunction unrealToProto = new CppFunction("SaveToProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(makeRef(ueArgument, true), makeRef(protoArgument, false)));

        final CppClass Specialization = new CppClass(castHelperSpecialized, /* superType = */ null, new ArrayList<>(),
                generateProtoCasts(oneOfDefinition));

        return Specialization;
    }

    private List<CppFunction> generateProtoCasts(OneOfDefinition oneOfDefinition) {
        final CppFunction protoToUnreal = new CppFunction("LoadFromProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(
                    new CppArgument(oneOfDefinition.messageProtoStruct.getType().makeConstant(Passage.ByRef), "Item"),
                    new CppArgument(oneOfDefinition.messageUnrealStruct.getType().makeRef(false, false), "UnrealMessage")
                    ));

        final CppFunction unrealToProto = new CppFunction("SaveToProto", CppType.plain("void", Kind.Primitive),
                Arrays.asList(
                    new CppArgument(oneOfDefinition.messageUnrealStruct.getType().makeConstant(Passage.ByRef), 
                    "UnrealMessage"),
                    new CppArgument(oneOfDefinition.messageProtoStruct.getType().makeRef(false, false), "OutItem"))
                    );

        generateCastBody(oneOfDefinition, protoToUnreal, true);
        generateCastBody(oneOfDefinition, unrealToProto, false);

        return Arrays.asList(protoToUnreal, unrealToProto);
    }

    private CppField getProtoField(final CppStruct messageProtoStruct, final OneOfInStruct currentOneOf,
            final CppField uefield) {
        return ProtoProcessor.ParseField(protoProvider,
                currentOneOf.oneOfElement.fields().get(currentOneOf.oneOfUnrealStruct.getFields().indexOf(uefield)));
    }

    private CppClass generateHelpersClass(final OneOfDefinition oneOfDefinition) {
        CppType type = CppType.plain("U" + oneOfDefinition.messageUnrealStruct.getType().getName() + "OneOfHelpers",
                Kind.Class);

        List<CppField> fields = new ArrayList<>();

        final List<CppFunction> methods = new ArrayList<>();

        methods.add(generateTryGet());

        methods.addAll(oneOfDefinition.oneOfStructs.stream().flatMap((oneOfStruct) -> {
            return generateHelperClassFunctions(oneOfStruct);
        }).collect(Collectors.toList()));

        return new CppClass(type, CppType.plain("UObject", Kind.Class), fields, methods);
    }

    private Stream<CppFunction> generateHelperClassFunctions(OneOfInStruct oneOfStruct) {
        CppArgument selfStructArgument = new CppArgument(oneOfStruct.oneOfUnrealStruct.getType().makeRef(), "Self");

        return oneOfStruct.oneOfElement.fields().stream().flatMap((fieldElement) -> {
            CppFunction createFunction = new CppFunction("Create" + "From" + generateArgumentName(fieldElement),
                    oneOfStruct.oneOfUnrealStruct.getType(),
                    Arrays.asList(new CppArgument(getOneOfFieldType(fieldElement).makeConstant(Passage.ByRef),
                            "OneOfValue")));

            createFunction.setBody(
                    String.join(System.lineSeparator(), "return " + oneOfStruct.oneOfUnrealStruct.getType().getName()
                            + "::Create(OneOfValue, " + fieldElement.tag() + ");"));

            CppFunction getFunction = new CppFunction("TryGet" + generateArgumentName(fieldElement),
                    CppType.plain("bool", Kind.Primitive), Arrays.asList(selfStructArgument,
                            new CppArgument(getOneOfFieldType(fieldElement).makeRef(), "OutOneOfValue")));

            getFunction.isConst = true;

            getFunction.setBody(String.join(System.lineSeparator(),
                    "return Self.TryGet(OutOneOfValue, " + fieldElement.tag() + ");"));

            CppFunction setFunction = new CppFunction("Set" + generateArgumentName(fieldElement),
                    CppType.plain("void", Kind.Primitive), Arrays.asList(selfStructArgument, new CppArgument(
                            getOneOfFieldType(fieldElement).makeConstant(Passage.ByRef), "OneOfValue")));

            setFunction
                    .setBody(String.join(System.lineSeparator(), "Self.Set(OneOfValue, " + fieldElement.tag() + ");"));

            return Arrays.asList(createFunction, getFunction, setFunction).stream().map((function) -> {
                function.addAnnotation(CppAnnotation.BlueprintCallable);

                function.isStatic = true;

                return function;
            });
        });
    }

    private String generateArgumentName(FieldElement fieldElement) {
        return fieldElement.name();
    }

    private CppType getOneOfFieldType(FieldElement oneOfField) {
        return ProtoProcessor.ParseField(ueProvider, oneOfField).getType();
    }

    private static CppEnum generateOneOfEnum(OneOfInStruct currentOneOf) {
        return currentOneOf.oneOfCaseEnum;
    }

    private Collection<CppRecord> generateOneOfImpl(final OneOfDefinition oneOfDefinition) {
        return Arrays.asList(generateHelpersClass(oneOfDefinition), generateOneOfCasts(oneOfDefinition));
    }

    public Collection<CppRecord> genOneOfs() {
        return oneOfs.stream().flatMap((x) -> {
            return generateOneOfImpl(x).stream();
        }).collect(Collectors.toList());

    }

    public BiFunction<CppStruct, CppStruct, String> createUeToProtoCastFunction() {
        return (protoStruct, ueStruct) -> {
            return "TOneOfHelpers<" + ueStruct.getType().getName() + ", " + protoStruct.getType().getName()
                    + ">::SaveToProto(Item, OutItem);";
        };
    }

    public BiFunction<CppStruct, CppStruct, String> createProtoToUeCastFunction() {
        return (protoStruct, ueStruct) -> {
            return "TOneOfHelpers<" + ueStruct.getType().getName() + ", " + protoStruct.getType().getName()
                    + ">::LoadFromProto(OutItem, Item);";
        };
    }

}
