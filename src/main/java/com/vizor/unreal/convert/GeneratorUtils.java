package com.vizor.unreal.convert;

import com.squareup.wire.schema.internal.parser.FieldElement;
import com.vizor.unreal.provider.TypesProvider;
import com.vizor.unreal.tree.CppClass;
import com.vizor.unreal.tree.CppType;
import com.vizor.unreal.util.Misc;

class GeneratorUtils
{
    public static CppType getType(TypesProvider provider, String typeName)
    {
        return provider.get(typeName);
    }

	public static String getUeFieldName(FieldElement fieldElement) {
		return Misc.sanitizeVarName(fieldElement.name(), fieldElement.type() == "bool");
	}
}