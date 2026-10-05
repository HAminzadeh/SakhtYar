package com.sakhtyar.config;
public record ConfigDefinition(String key,String category,String label,String description,String dataType,boolean secret,String applyMode,String defaultValue) {}