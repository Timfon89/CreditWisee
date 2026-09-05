package com.creditwise.app.data.model;

/** Self-reported employment type — softens how strictly income-regularity is judged. */
public enum EmploymentType {
    STUDENT("Студент", "Учитываем небольшую или отсутствующую кредитную историю."),
    FREELANCER("Фрилансер / самозанятый", "Более мягкий порог для «регулярности поступлений» — доход по природе неровный."),
    EMPLOYEE("Штатный сотрудник", "Стандартные условия расчёта регулярности поступлений.");

    private final String displayName;
    private final String description;

    EmploymentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
