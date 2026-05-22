package com.hik.osp.util;

public class NamingUtils {

    private NamingUtils() {}

    /**
     * Convert snake_case or kebab-case to PascalCase.
     */
    public static String toPascalCase(String name) {
        if (name == null || name.isEmpty()) return name;
        String[] parts = name.replace("-", "_").split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Convert snake_case or kebab-case to camelCase.
     */
    public static String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal == null || pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }
}
