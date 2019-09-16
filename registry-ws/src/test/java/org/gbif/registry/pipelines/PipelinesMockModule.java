package org.gbif.registry.pipelines;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.mockito.Mockito;

public class PipelinesMockModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(PipelinesHistoryTrackingService.class)
        .to(PipelinesCoordinatorTrackingServiceImpl.class)
        .in(Scopes.SINGLETON);
    bind(MetricsHandler.class).toInstance(Mockito.mock(MetricsHandler.class));
  }
}