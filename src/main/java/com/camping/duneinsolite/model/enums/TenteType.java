package com.camping.duneinsolite.model.enums;

public enum TenteType {
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3),
    X4(4),
    X5(5),
    X6(6),
    X7(7);

    private final int capacity;

    TenteType(int capacity) { this.capacity = capacity; }

    public int getCapacity() { return capacity; }
}
