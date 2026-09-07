package com.kaveenk.onlydragons.gametests;

/**
 * One server-thread test program in an isolated Paper boot.
 * The scenario must eventually call {@link ScenarioContext#finish()} or fail;
 * returning from {@link #start} alone does not complete asynchronous tick stages.
 * The external runner owns the wall-clock deadline and both JVMs, while the
 * context owns scenario-local tasks, listeners, entities and reversible setup.
 */
@FunctionalInterface
public interface Scenario {
    /**
     * Begins the scenario on the Paper server thread.
     * @param context active report and cleanup owner for this single scenario
     * @throws Exception when setup fails; the harness converts it into a failed report
     */
    void start(ScenarioContext context) throws Exception;
}
