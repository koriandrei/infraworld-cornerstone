/*
 * Copyright 2018 Vizor Games LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.vizor.unreal.convert;

import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import com.vizor.unreal.config.Config;
import com.vizor.unreal.provider.ProtoTypesProvider;
import com.vizor.unreal.provider.TypesProvider;
import com.vizor.unreal.provider.UnrealTypesProvider;
import com.vizor.unreal.tree.CppAnnotation;
import com.vizor.unreal.tree.CppClass;
import com.vizor.unreal.tree.CppDelegate;
import com.vizor.unreal.tree.CppEnum;
import com.vizor.unreal.tree.CppField;
import com.vizor.unreal.tree.CppNamespace;
import com.vizor.unreal.tree.CppRecord;
import com.vizor.unreal.tree.CppStruct;
import com.vizor.unreal.tree.CppType;
import com.vizor.unreal.tree.CppType.Kind;
import com.vizor.unreal.tree.preprocessor.CppInclude;
import com.vizor.unreal.tree.preprocessor.CppMacroIf;
import com.vizor.unreal.tree.preprocessor.CppPragma;
import com.vizor.unreal.util.MessageOrderResolver;
import com.vizor.unreal.util.Tuple;
import com.vizor.unreal.writer.CppPrinter;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.squareup.wire.schema.Field.Label.REPEATED;
import static com.vizor.unreal.tree.CppAnnotation.BlueprintReadWrite;
import static com.vizor.unreal.tree.CppAnnotation.BlueprintType;
import static com.vizor.unreal.tree.CppAnnotation.DisplayName;
import static com.vizor.unreal.tree.CppAnnotation.Transient;
import static com.vizor.unreal.tree.CppRecord.Residence.Cpp;
import static com.vizor.unreal.tree.CppRecord.Residence.Header;
import static com.vizor.unreal.tree.CppType.Kind.Enum;
import static com.vizor.unreal.tree.CppType.Kind.Struct;
import static com.vizor.unreal.tree.CppType.plain;
import static com.vizor.unreal.util.Misc.reorder;
import static com.vizor.unreal.util.Misc.snakeCaseToCamelCase;
import static com.vizor.unreal.util.Misc.stringIsNullOrEmpty;
import static com.vizor.unreal.util.Tuple.of;
import static java.io.File.separator;
import static java.lang.String.join;
import static java.nio.file.Paths.get;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.apache.logging.log4j.LogManager.getLogger;

class ProtoProcessor implements Runnable
{
    private static final Logger log = getLogger(ProtoProcessor.class);

    private final ProtoFileElement parse;
    private final Path pathToProto;
    private final Path pathToConverted;
    private final String moduleName;

    private final String wrapperName;

    private final String className;
    private final CppNamespace packageNamespace;

    private final TypesProvider ueProvider = new UnrealTypesProvider();
    private final TypesProvider protoProvider = new ProtoTypesProvider();

    ProtoProcessor(
        final ProtoFileElement parse,
        final Path pathToProto,
        final Path pathToConverted,
        final String moduleName
    )
    {
        this.parse = requireNonNull(parse);
        this.pathToProto = requireNonNull(pathToProto);
        this.pathToConverted = requireNonNull(pathToConverted);
        this.moduleName = requireNonNull(moduleName);

        this.wrapperName = removeExtension(pathToProto.toFile().getName());

        this.className = snakeCaseToCamelCase(wrapperName);

//        if (parse.packageName() == null)
//            throw new RuntimeException("package filed in proto file is required for cornerstone");

        this.packageNamespace = new CppNamespace(parse.packageName());
    }

    @Override
    public void run()
    {
        final List<ServiceElement> services = parse.services();

        for (final TypeElement t : parse.types())
        {
            ueProvider.register(t.name(), ueNamedType(className, t));
            protoProvider.register(t.name(), cppNamedType(t));

            if (t instanceof MessageElement) 
            {
                ((MessageElement)t).oneOfs().forEach(
                    (OneOfElement el) -> {
                        ueProvider.register(el.name(), ueOneOfType(className, el));
                    }
                );
            }
        }

        final List<Tuple<CppStruct, CppStruct>> castAssociations = new ArrayList<>();
        final List<CppStruct> unrealStructures = new ArrayList<>();

        final List<CppEnum> ueEnums = new ArrayList<>();

        

        final List<OneOfDefinition> oneOfs = new ArrayList<>();

        // At this moment, we have all types registered in both type providers
        for (final TypeElement s : parse.types())
        {
            if (s instanceof MessageElement)
            {
                final MessageElement messageElement = (MessageElement) s;

                final CppStruct protoStruct = extractStruct(protoProvider, messageElement);

                final CppStruct ueStruct = extractStruct(ueProvider, messageElement);

                List<OneOfInStruct> oneOfStructs = new ArrayList<>();
                
                for (OneOfElement oe : messageElement.oneOfs())
                {
                    final OneOfInStruct oneOf = extractOneOf(ueProvider, oe);
                    
                    unrealStructures.add(oneOf.oneOfUnrealStruct);
                    
                    ueEnums.add(oneOf.oneOfCaseEnum);
                    
                    oneOfStructs.add(oneOf);
                }
                
                OneOfDefinition oneOf = new OneOfDefinition(oneOfStructs, ueStruct, protoStruct, messageElement);
                
                oneOfs.add(oneOf);

                log.debug("Found type cast {} -> {}", ueStruct.getType(), protoStruct.getType());

                castAssociations.add(of(protoStruct, ueStruct));
                unrealStructures.add(ueStruct);
            }
            else if (s instanceof EnumElement)
            {
                ueEnums.add(extractEnum(ueProvider, (EnumElement) s));
            }
            else
            {
                throw new RuntimeException("Unknown type: '" + s.getClass().getName() + "'");
            }
        }

        // Topologically sort structures
        final MessageOrderResolver resolver = new MessageOrderResolver();
        final int[] indices = resolver.sortByInclusion(unrealStructures);

        // Then reorder data types
        reorder(unrealStructures, indices);

        // reorder(castAssociations, indices);

        castAssociations.sort(
            (Tuple<CppStruct, CppStruct> Lhs, Tuple<CppStruct, CppStruct> Rhs) -> 
            { return unrealStructures.indexOf(Lhs.first()) - (unrealStructures.indexOf(Rhs.first())); }
        );


        // Generate OneOf wrappers
        final OneOfGenerator oneOfGenerator = new OneOfGenerator(ueProvider, protoProvider, oneOfs);

        Collection<CppRecord> extraRecords = new ArrayList<>();

        extraRecords.addAll(oneOfGenerator.genOneOfs());


        BiFunction<CppStruct, CppStruct, String> ueToProtoOneofCast = oneOfGenerator.createUeToProtoCastFunction();
        BiFunction<CppStruct, CppStruct, String> protoToUeOneofCast = oneOfGenerator.createProtoToUeCastFunction();

        final CppNamespace casts = new CastGenerator(Arrays.asList(ueToProtoOneofCast), Arrays.asList(protoToUeOneofCast) ).genCasts(castAssociations);

        log.debug("Found structures (sorted): {}", () ->
            unrealStructures.stream().map(s -> s.getType().getName()).collect(joining(", ", "[", "]")
        ));

        // Generate RPC workers
        final ClientWorkerGenerator clientWorkerGenerator = new ClientWorkerGenerator(services, ueProvider, parse);
        final List<CppClass> workers = clientWorkerGenerator.genClientClass();

        // Generate RPC clients
        final List<CppClass> clients = new ArrayList<>(services.size());
        final List<CppDelegate> dispatchers = new ArrayList<>(services.size());

        for (int i = 0; i < services.size(); i++)
        {
            final ServiceElement service = services.get(i);
            final CppClass worker = workers.get(i);

            final ClientGenerator cg = new ClientGenerator(service, ueProvider, worker.getType());

            clients.add(cg.genClientClass());
            dispatchers.addAll(cg.getDelegates());
        }

        final String pathToProtoStr = removeExtension(pathToProto.toString());
        final String outDirPath = join(separator, pathToConverted.toString(), pathToProtoStr);

        // Should create an output directories if does not exit.
        @SuppressWarnings("unused")
        final boolean ignore = get(outDirPath).toFile().mkdirs();

        final List<CppInclude> headerIncludes = asList(
            // header
            new CppInclude(Header, "CoreMinimal.h"),
            new CppInclude(Header, "Conduit.h"),
            new CppInclude(Header, "GenUtils.h"),
            new CppInclude(Header, "RpcClient.h"),
            new CppInclude(Header, className + ".generated.h")
        );

        final Config config = Config.get();

        // TODO: Fix paths
        final String generatedIncludeName = join("/", config.getWrappersPath(),
                removeExtension(pathToProtoStr), wrapperName);

        // code. mutable to allow
        final List<CppRecord> cppIncludes = new ArrayList<>(asList(
            new CppInclude(Cpp, className + ".h"),
            new CppInclude(Cpp, "RpcClientWorker.h"),
            new CppInclude(Cpp, "CastUtils.h"),

            new CppInclude(Cpp, "GrpcIncludesBegin.h"),

            new CppInclude(Cpp, "grpc/support/log.h", true),
            new CppInclude(Cpp, "grpc++/channel.h", true),

            new CppInclude(Cpp, generatedIncludeName + ".pb.hpp", false),
            new CppInclude(Cpp, generatedIncludeName + ".grpc.pb.hpp", false),
            new CppInclude(Cpp, "ChannelProvider.h", false),

            new CppInclude(Cpp, "GrpcIncludesEnd.h")
        ));

        if (!stringIsNullOrEmpty(config.getPrecompiledHeader()))
            cppIncludes.add(0, new CppInclude(Cpp, config.getPrecompiledHeader(), false));

        final Path outFilePath = get(outDirPath, className);

        try (final CppPrinter p = new CppPrinter(outFilePath, moduleName.toUpperCase()))
        {
            headerIncludes.forEach(i -> i.accept(p));
            p.newLine();

            cppIncludes.forEach(i -> i.accept(p));
            p.newLine();

            // Write enums and structs
            p.writeInlineComment("Enums:");
            ueEnums.forEach(e -> e.accept(p).newLine());

            p.writeInlineComment("Structures:");
            unrealStructures.forEach(s -> s.accept(p).newLine());

            p.writeInlineComment("Extras:");
            extraRecords.forEach(s -> s.accept(p).newLine());

            p.writeInlineComment("Forward class definitions (for delegates)");
            clients.forEach(c -> p.write("class ").write(c.getType().toString()).writeLine(";"));
            p.newLine();

            p.writeInlineComment("Dispatcher delegates");
            dispatchers.forEach(d -> d.accept(p).newLine());
            p.newLine();

            // Write casts to the CPP file
            casts.accept(p).newLine();

            // Workers are being written to the *.cpp file, have to write them before
            workers.forEach(c -> c.accept(p).newLine());

            clients.forEach(w -> w.accept(p).newLine());
        }
    }

    private OneOfInStruct extractOneOf(final TypesProvider provider, final OneOfElement oe) {
        final CppType oneOfType = provider.get(oe.name());

        final List<CppAnnotation> fieldAnnotations = new ArrayList<>();

        fieldAnnotations.add(Transient);
        fieldAnnotations.add(BlueprintReadWrite);

        final List<CppField> fields = new ArrayList<>();
        
        final List<FieldElement> fieldElements = new ArrayList<>();

        for (final FieldElement fe : oe.fields()) {
            final CppType fieldUeType = provider.get(fe.type());

            final CppField ueField = new CppField(fieldUeType, fe.name());
            
            ueField.addAnnotation(fieldAnnotations);
            fields.add(ueField);

            fieldElements.add(fe);
        }

        CppEnum oneOfCaseEnum = new CppEnum(CppType.plain(oe.name() + "case", Enum), oe.fields().stream()
                .collect(Collectors.toMap((FieldElement fe) -> fe.name(), (FieldElement fe) -> fe.tag())));

        List<CppField> structFields = new ArrayList<>(fields);

        structFields.add(new CppField(oneOfCaseEnum.getType(), "OneOfCase"));

        final CppClass oneOfStruct = new CppClass(
            oneOfType, 
            CppType.wildcardGeneric("TOneOf", Kind.Struct, fields.size()).makeGeneric(
                oe.fields().stream().map((fe)->{
                    return CppType.wildcardGeneric("TTypeIntegerPair", Kind.Struct, 2).makeGeneric(
                        fields.get(oe.fields().indexOf(fe)).getType(), 

                        // TODO: This must be an int template argument,
                        // and not a type of int. Its value should be
                        // fe.tag()
                        CppType.plain("int", Kind.Primitive)); // fe.tag()
                }).collect(Collectors.toList())
                )
            ,
            structFields,
            new ArrayList<>()
        );

        return new OneOfInStruct(oneOfCaseEnum, fields, oneOfStruct, oe);
    }

    private CppStruct extractStruct(final TypesProvider provider, final MessageElement me)
    {
        final CppType type = provider.get(me.name());

        final List<CppAnnotation> fieldAnnotations = new ArrayList<>();

        fieldAnnotations.add(Transient);
        fieldAnnotations.add(BlueprintReadWrite);

        final List<CppField> fields = new ArrayList<>();
        for (final FieldElement fe : me.fields())
        {
            final CppField field = ParseField(provider, fe);

            field.addAnnotation(fieldAnnotations);
            fields.add(field);
        }

        for (final OneOfElement oe : me.oneOfs())
        {
            if (provider != ueProvider)
            {
                continue;
            }

            final CppType oneOfType = provider.get(oe.name());
            
            final CppField field = new CppField(oneOfType, oe.name());

            fields.add(field);
        }

        final CppStruct struct = new CppStruct(type, fields);

        struct.addAnnotation(DisplayName, className + " " + me.name());

        if (!me.documentation().isEmpty())
            struct.javaDoc.set(me.documentation());

        struct.addAnnotation(BlueprintType);

        struct.setResidence(Header);
        return struct;
    }

    public static CppField ParseField(TypesProvider provider, FieldElement fe) {
        
        // Get the type
        final CppType ueType = provider.get(fe.type());

        // If the field is repeated - make a TArray<?> of type.
        final CppField field;
        if (fe.label() == REPEATED)
        {
            final CppType ueArrayType = provider.arrayOf(ueType);
            field = new CppField(ueArrayType, provider.fixFieldName(fe.name(), false));
        }
        else
        {
            final String fieldName = provider.fixFieldName(fe.name(), ueType.isA(boolean.class));
            field = new CppField(ueType, fieldName);
        }

        // Add docs if has any
        final String sourceDoc = fe.documentation();
        if (!sourceDoc.isEmpty())
            field.javaDoc.set(sourceDoc);

        return field;
    }

    private CppEnum extractEnum(final TypesProvider provider, final EnumElement ee)
    {
        final CppEnum cppEnum = new CppEnum(provider.get(ee.name()), ee.constants().stream()
                .collect(toMap(m -> provider.fixFieldName(m.name(), false), EnumConstantElement::tag)));

        if (!ee.documentation().isEmpty())
            cppEnum.getJavaDoc().set(ee.documentation());

        cppEnum.addAnnotation(BlueprintType);
        cppEnum.addAnnotation(DisplayName, className + " " + ee.name());

        cppEnum.setResidence(Header);
        return cppEnum;
    }

    private CppType ueNamedType(final String serviceName, final TypeElement el)
    {
        if (el instanceof MessageElement)
            return plain("F" + serviceName + "_" + el.name(), Struct);
        else if (el instanceof EnumElement)
            return plain("E" + serviceName + "_" + el.name(), Enum);
        else
            throw new RuntimeException("Unknown type: '" + el.getClass().getName() + "'");
    }

    private CppType ueOneOfType(final String serviceName, final OneOfElement el) 
    {
        return plain("F" + serviceName + "_" + "OneOf" + "_" + el.name(), Struct);
    }

    private CppType cppNamedType(final TypeElement el)
    {
        if (el instanceof MessageElement)
        {
            final CppType mt = plain(el.name(), Struct);

            if (packageNamespace.hasName())
                mt.setNamespaces(packageNamespace);

            return mt;
        }
        else if (el instanceof EnumElement)
        {
            final CppType et = plain(el.name(), Enum);

            if (packageNamespace.hasName())
                et.setNamespaces(packageNamespace);

            return et;
        }
        else
        {
            throw new RuntimeException("Unknown type: '" + el.getClass().getName() + "'");
        }
    }
}
