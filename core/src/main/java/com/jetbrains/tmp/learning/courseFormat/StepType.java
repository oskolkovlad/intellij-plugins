package com.jetbrains.tmp.learning.courseFormat;

import org.jetbrains.annotations.NotNull;

/**
 * @author meanmail
 */
public enum StepType {
    UNKNOWN, CODE, TEXT, VIDEO;

    @NotNull
    public static StepType of(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}