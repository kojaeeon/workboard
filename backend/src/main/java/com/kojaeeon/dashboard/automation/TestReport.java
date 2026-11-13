package com.kojaeeon.dashboard.automation;

import java.util.*;

/** A worker publishes one complete immutable-file snapshot after every state change. */
public class TestReport {

  public String title;
  public String status = "starting";
  public String error = "";
  public List<StepResult> steps;

  public static class StepResult {

    public int number;
    public String action,
      target,
      status = "pending",
      strategy = "",
      error = "";
    public long duration_ms;

    public StepResult() {}

    public StepResult(int number, ScenarioParser.Step step) {
      this.number = number;
      action = step.action();
      target = step.target();
    }
  }

  public TestReport() {}

  public TestReport(ScenarioParser.Scenario scenario) {
    title = scenario.title();
    steps = new ArrayList<>();
    for (int i = 0; i < scenario.steps().size(); i++) steps.add(
      new StepResult(i + 1, scenario.steps().get(i))
    );
  }
}
