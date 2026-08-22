package dev.ermer.mythglass.stage.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Aktiviert den Heartbeat, der die SSE-Verbindungen offen hält. */
@Configuration
@EnableScheduling
class StageSchedulingConfiguration {}
