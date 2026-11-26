package com.minemeboost.clienttest;

/** Only MineXray's checks (fast feedback while working on it); the full suite runs them too. */
class XraySuite extends XrayTests {
    XraySuite() {
        super("xray");
    }

    @Override
    protected void build() {
        step(200, this::setupArena);
        build36();
        step(5, c -> {});
    }
}
