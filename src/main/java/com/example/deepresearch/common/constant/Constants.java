package com.example.deepresearch.common.constant;

import java.util.regex.Pattern;

public final class Constants {

    /** 匹配引用标记：[WEB1]/[LOCAL3] */
    public static final Pattern CITATION_PATTERN = Pattern.compile("\\[(WEB|LOCAL)\\d+]");
}
