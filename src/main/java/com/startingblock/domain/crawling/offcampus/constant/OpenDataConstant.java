package com.startingblock.domain.crawling.offcampus.constant;

public final class OpenDataConstant {

    private OpenDataConstant() {}

    public static final String EMAIL_XPATH = "//*[@id=\"contentViewHtml\"]/div/div/div/div[2]/div[5]/ul";
    public static final String EMAIL_REGEX = "[a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}";
    public static final String ONCE_POPUP = "//*[@id=\"t_c_2955\"]";
    public static final String CLOSE_POPUP = "//*[@id=\"cmn_pop_pbancList_010\"]/div[2]/button";
}
