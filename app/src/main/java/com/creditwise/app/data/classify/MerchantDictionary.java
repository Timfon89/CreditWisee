package com.creditwise.app.data.classify;

import com.creditwise.app.data.model.ExpenseCategory;

/** Keyword rules that map a raw merchant / description string to a spending category. */
public final class MerchantDictionary {

    private MerchantDictionary() {}

    private static final String[] GAMBLING = {
            "WINLINE", "BETBOOM", "1XBET", "1WIN", "FONBET", "FON.BET", "LIGASTAVOK",
            "LIGA STAVOK", "OLIMP", "OLIMPBET", "PARI", "PARIMATCH", "MELBET", "MARATHONBET",
            "BETCITY", "LEON", "BETERA", "PIN-UP", "PINUP"
    };
    private static final String[] TAXI = {
            "*GO", "*TAXI", "YANDEX*4121", "YANDEX GO", "CITYMOBIL", "DIDI", "MAXIM TAXI", "TRUDNO TAXI"
    };
    private static final String[] FOOD_DELIVERY = {
            "*EDA", "YANDEX*5814", "*DOSTAVKA", "YANDEX*4215", "DELIVERY CLUB", "DELIVERYCLUB",
            "SAMOKAT", "KUPER", "SBERMARKET", "VKUSVILL EXPRESS", "YANDEX LAVKA", "LAVKA"
    };
    private static final String[] KICKSHARING = {
            "URENT", "WHOOSH", "YM*URENT", "YM URENT", "SAMOKAT SHARING", "BIKE"
    };
    private static final String[] GROCERIES = {
            "KRASNOE", "BELOE", "PYATEROCHKA", "PYATEROCHKA", "MAGNIT", "PEREKRESTOK", "PEREKRYOSTOK",
            "LENTA", "MONETKA", "DIXY", "ZHIZNMART", "VKUSVILL", "MAVT-VINOTEKA", "M-MYATA",
            "MUNDSHTUK", "TABAK", "KING-KONG", "MAGIC SMOKE", "TUK-TUK", "MARIA-RA", "AUCHAN",
            "GLOBUS", "MIRATORG", "URALSKAYA FABRIKA"
    };
    private static final String[] SHOPPING = {
            "OZON", "WILDBERRIES", "LAMODA", "ALIEXPRESS", "СДЭК", "CDEK", "OFFPRICE", "ОФФПРАЙС",
            "ELDORADO", "DNS", "MVIDEO", "M.VIDEO", "IKEA", "HOFF", "SPORTMASTER", "ЯНДЕКС МАРКЕТ",
            "YANDEX MARKET", "POPOLARE", "SHKOLKOVO SHOP"
    };
    private static final String[] SUBSCRIPTIONS = {
            "TELEGRAM", "OTO*TELEGRAM", "HITVPN", "HIT VPN", " VPN", "VPN.", "HTV", "NETFLIX",
            "SPOTIFY", "YOUTUBE", "KINOPOISK", "YANDEX PLUS", "YANDEX.PLUS", "YANDEX SPLIT",
            "YANDEX.SPLIT", "SPLIT", "DINODROP", "REGULAR CHARGE", "PODPISKA", "SUBSCRIPTION",
            "APPLE.COM", "GOOGLE ", "ITUNES", "VK MUSIC", "SBERPRIME", "PREMIUM"
    };
    private static final String[] UTILITIES = {
            "ROSTELECOM", "ROSTELEKOM", "РОСТЕЛЕКОМ", "T2 ", "TELE2", "MTS", "МТС", "MEGAFON",
            "МЕГАФОН", "BEELINE", "БИЛАЙН", "YOTA", "DOM.RU", "ENERGOSBYT", "GORGAZ", "TNS ENERGO",
            "KOMMUNAL", "ZHKH", "TSK", "UK "
    };
    private static final String[] HEALTH = {
            "APTEKA", "APTEK", "ZHIVIKA", "36.6", "36,6", "RIGLA", "GORZDRAV", "DENT", "STOMATOLOG",
            "KLINIKA", "MEDCENTR", "INVITRO", "GEMOTEST", "OPTIKA"
    };
    private static final String[] EDUCATION = {
            "ШКОЛКОВО", "SHKOLKOVO", "SKYENG", "UCHI.RU", "UCHIRU", "FOXFORD", "NETOLOGY",
            "GEEKBRAINS", "YANDEX PRAKTIKUM", "PRACTICUM", "COURSERA", "STEPIK", "UMSCHOOL",
            "MAXIMUM EDUCATION", "YANDEX SPLIT MOSKVA"
    };
    private static final String[] PUBLIC_TRANSPORT = {
            "AVTOVOKZAL", "AVTOVOXAL", "RZD", "РЖД", "AEROEXPRESS", "METRO", "METROPOLITEN",
            "TROLLEYBUS", "PODOROZHNIK", "TRANSPORT", "GORTRANS", "TUTU", "TUTU_SBP", "AVIASALES",
            "POBEDA", "AEROFLOT", "S7", "URALSKIE AVIALINII"
    };

    public static boolean isGambling(String upper) {
        return containsAny(upper, GAMBLING);
    }

    /** Returns a category for a debit, or {@code null} if no keyword matched. */
    public static ExpenseCategory categoryFor(String upper) {
        if (containsAny(upper, TAXI)) return ExpenseCategory.TAXI;
        if (containsAny(upper, FOOD_DELIVERY)) return ExpenseCategory.FOOD_DELIVERY;
        if (containsAny(upper, KICKSHARING)) return ExpenseCategory.KICKSHARING;
        if (containsAny(upper, SUBSCRIPTIONS)) return ExpenseCategory.SUBSCRIPTIONS;
        if (containsAny(upper, UTILITIES)) return ExpenseCategory.UTILITIES;
        if (containsAny(upper, EDUCATION)) return ExpenseCategory.EDUCATION;
        if (containsAny(upper, HEALTH)) return ExpenseCategory.HEALTH;
        if (containsAny(upper, PUBLIC_TRANSPORT)) return ExpenseCategory.PUBLIC_TRANSPORT;
        if (containsAny(upper, GROCERIES)) return ExpenseCategory.GROCERIES;
        if (containsAny(upper, SHOPPING)) return ExpenseCategory.SHOPPING;
        return null;
    }

    private static boolean containsAny(String haystack, String[] needles) {
        if (haystack == null) return false;
        for (String n : needles) {
            if (!n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }
}
