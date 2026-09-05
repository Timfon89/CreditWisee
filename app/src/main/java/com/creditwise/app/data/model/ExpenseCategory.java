package com.creditwise.app.data.model;

public enum ExpenseCategory {
    GROCERIES("Супермаркеты"),
    CAFE("Кафе и рестораны"),
    FOOD_DELIVERY("Доставка еды"),
    TAXI("Такси"),
    KICKSHARING("Самокаты и аренда"),
    PUBLIC_TRANSPORT("Общественный транспорт"),
    SUBSCRIPTIONS("Подписки и сервисы"),
    UTILITIES("Связь и ЖКХ"),
    HEALTH("Здоровье и аптеки"),
    EDUCATION("Образование"),
    SHOPPING("Покупки и маркетплейсы"),
    ENTERTAINMENT("Развлечения"),
    GAMBLING("Ставки и азартные игры"),
    CASH("Снятие наличных"),
    TRANSFERS_OUT("Переводы людям"),
    MFO_PAYMENT("Платежи МФО/микрозаймы"),
    OTHER("Прочее");

    private final String displayName;

    ExpenseCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
