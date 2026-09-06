package com.kaveenk.onlydragons.gametests;

/** Server-thread scenario. Complete explicitly; the outer runner enforces a wall-clock timeout. */
@FunctionalInterface
public interface Scenario {
    void start(ScenarioContext context) throws Exception;
}
