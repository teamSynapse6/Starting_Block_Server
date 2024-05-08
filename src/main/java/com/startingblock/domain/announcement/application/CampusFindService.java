package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.user.domain.User;

public class CampusFindService {
    private CampusFindService() {}

    public static University findUserUniversity(User user) {
        switch (user.getUniversity()) {
            case "건국대학교" -> {
                return University.KON_KUK;
            }
            case "서울과학기술대학교" -> {
                return University.SEOUL_TECH;
            }
            case "서울대학교" -> {
                return University.SEOUL;
            }
            case "성균관대학교" -> {
                return University.SUNG_KYUN_KWAN;
            }
            case "연세대학교" -> {
                return University.YON_SEI;
            }
            case "한양대학교" -> {
                return University.HAN_YANG;
            }
            case "이화여자대학교" -> {
                return University.EWHA;
            }
            case "경희대학교" -> {
                return University.KYUNG_HEE;
            }
            case "고려대학교" -> {
                return University.KOREA;
            }
            case "한국외국어대학교" -> {
                return University.HUFS;
            }
        }
        return null;
    }
}
