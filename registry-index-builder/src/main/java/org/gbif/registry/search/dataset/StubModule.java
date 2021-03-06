package org.gbif.registry.search.dataset;

import org.gbif.registry.doi.generator.DoiGenerator;
import org.gbif.registry.doi.handler.DataCiteDoiHandlerStrategy;
import org.gbif.registry.stubs.DoiGeneratorStub;
import org.gbif.registry.stubs.DoiHandlerStrategyStub;
import org.gbif.registry.stubs.EditorAuthorizationServiceStub;
import org.gbif.registry.stubs.OrganizationEndorsementServiceStub;
import org.gbif.registry.ws.security.EditorAuthorizationService;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import static org.gbif.registry.ws.surety.OrganizationSuretyModule.ORGANIZATION_ENDORSEMENT_SERVICE_TYPE_REF;

/**
 *
 */
class StubModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DoiGenerator.class).to(DoiGeneratorStub.class);
    bind(DataCiteDoiHandlerStrategy.class).to(DoiHandlerStrategyStub.class);
    bind(EditorAuthorizationService.class).to(EditorAuthorizationServiceStub.class);
    bind(ORGANIZATION_ENDORSEMENT_SERVICE_TYPE_REF).to(OrganizationEndorsementServiceStub.class).in(Scopes.SINGLETON);
  }
}
