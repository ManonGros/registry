package org.gbif.registry.collections;

import org.gbif.api.model.collections.Address;
import org.gbif.api.model.collections.Institution;
import org.gbif.api.model.collections.Person;
import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.service.collections.InstitutionService;
import org.gbif.api.service.collections.PersonService;
import org.gbif.registry.ws.resources.collections.InstitutionResource;
import org.gbif.registry.ws.resources.collections.PersonResource;
import org.gbif.ws.client.filter.SimplePrincipalProvider;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.gbif.registry.guice.RegistryTestModules.webservice;
import static org.gbif.registry.guice.RegistryTestModules.webserviceClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstitutionIT extends ExtendedCollectionEntityTest<Institution> {

  private static final String NAME = "name";
  private static final String DESCRIPTION = "dummy description";
  private static final URI HOMEPAGE = URI.create("http://dummy");
  private static final String CODE_UPDATED = "code2";
  private static final String NAME_UPDATED = "name2";
  private static final String DESCRIPTION_UPDATED = "dummy description updated";
  private static final String ADDITIONAL_NAME = "additional name";

  private final InstitutionService institutionService;
  private final PersonService personService;

  @Parameters
  public static Iterable<Object[]> data() {
    final Injector client = webserviceClient();
    final Injector webservice = webservice();
    return ImmutableList.<Object[]>of(
        new Object[] {
          webservice.getInstance(InstitutionResource.class),
          webservice.getInstance(PersonResource.class),
          null
        },
        new Object[] {
          client.getInstance(InstitutionService.class),
          client.getInstance(PersonService.class),
          client.getInstance(SimplePrincipalProvider.class)
        });
  }

  public InstitutionIT(
      InstitutionService institutionService,
      PersonService personService,
      @Nullable SimplePrincipalProvider pp) {
    super(
        institutionService,
        institutionService,
        institutionService,
        institutionService,
        institutionService,
        personService,
        pp);
    this.institutionService = institutionService;
    this.personService = personService;
  }

  @Test
  public void listWithoutParametersTest() {
    Institution institution1 = newEntity();
    UUID key1 = institutionService.create(institution1);

    Institution institution2 = newEntity();
    UUID key2 = institutionService.create(institution2);

    Institution institution3 = newEntity();
    UUID key3 = institutionService.create(institution3);

    PagingResponse<Institution> response = institutionService.list(null, null, null, null, PAGE.apply(5, 0L));
    assertEquals(3, response.getResults().size());

    institutionService.delete(key3);

    response = institutionService.list(null, null, null, null, PAGE.apply(5, 0L));
    assertEquals(2, response.getResults().size());

    response = institutionService.list(null, null, null, null, PAGE.apply(1, 0L));
    assertEquals(1, response.getResults().size());

    response = institutionService.list(null, null, null, null, PAGE.apply(0, 0L));
    assertEquals(0, response.getResults().size());
  }

  @Test
  public void listTest() {
    Institution institution1 = newEntity();
    institution1.setCode("c1");
    institution1.setName("n1");
    Address address = new Address();
    address.setAddress("dummy address");
    address.setCity("city");
    institution1.setAddress(address);
    UUID key1 = institutionService.create(institution1);

    Institution institution2 = newEntity();
    institution2.setCode("c2");
    institution2.setName("n2");
    Address address2 = new Address();
    address2.setAddress("dummy address2");
    address2.setCity("city2");
    institution2.setAddress(address2);
    UUID key2 = institutionService.create(institution2);

    Pageable page = PAGE.apply(5, 0L);
    PagingResponse<Institution> response = institutionService.list("dummy", null, null, null, page);
    assertEquals(2, response.getResults().size());

    // empty queries are ignored and return all elements
    response = institutionService.list("", null, null, null,page);
    assertEquals(2, response.getResults().size());

    response = institutionService.list("city", null, null, null,page);
    assertEquals(1, response.getResults().size());
    assertEquals(key1, response.getResults().get(0).getKey());

    response = institutionService.list("city2", null, null, null,page);
    assertEquals(1, response.getResults().size());
    assertEquals(key2, response.getResults().get(0).getKey());

    // code and name params
    assertEquals(1, institutionService.list(null, null, "c1", null, page).getResults().size());
    assertEquals(1, institutionService.list(null, null, null, "n2", page).getResults().size());
    assertEquals(1, institutionService.list(null, null, "c1", "n1", page).getResults().size());
    assertEquals(0, institutionService.list(null, null, "c2", "n1", page).getResults().size());

    // query param
    assertEquals(2, institutionService.list("c", null, null, null, page).getResults().size());
    assertEquals(2, institutionService.list("dum add", null, null, null, page).getResults().size());
    assertEquals(0, institutionService.list("<", null, null, null, page).getResults().size());
    assertEquals(0, institutionService.list("\"<\"", null, null, null, page).getResults().size());
    assertEquals(2, institutionService.list(null, null, null, null, page).getResults().size());
    assertEquals(2, institutionService.list("  ", null, null, null, page).getResults().size());

    // update address
    institution2 = institutionService.get(key2);
    institution2.getAddress().setCity("city3");
    institutionService.update(institution2);
    assertEquals(1, institutionService.list("city3", null, null, null, page).getResults().size());

    institutionService.delete(key2);
    assertEquals(0, institutionService.list("city3", null, null, null, page).getResults().size());
  }

  @Test
  public void listByContactTest() {
    // persons
    Person person1 = new Person();
    person1.setFirstName("first name");
    UUID personKey1 = personService.create(person1);

    Person person2 = new Person();
    person2.setFirstName("first name2");
    UUID personKey2 = personService.create(person2);

    // institutions
    Institution institution1 = newEntity();
    UUID instutionKey1 = institutionService.create(institution1);

    Institution institution2 = newEntity();
    UUID instutionKey2 = institutionService.create(institution2);

    // add contacts
    institutionService.addContact(instutionKey1, personKey1);
    institutionService.addContact(instutionKey1, personKey2);
    institutionService.addContact(instutionKey2, personKey2);

    assertEquals(1, institutionService.list(null, personKey1, null, null,PAGE.apply(5, 0L)).getResults().size());
    assertEquals(2, institutionService.list(null, personKey2, null, null,PAGE.apply(5, 0L)).getResults().size());
    assertEquals(0, institutionService.list(null, UUID.randomUUID(), null, null,PAGE.apply(5, 0L)).getResults().size());

    institutionService.removeContact(instutionKey1, personKey2);
    assertEquals(1, institutionService.list(null, personKey2, null, null,PAGE.apply(5, 0L)).getResults().size());
  }

  @Override
  protected Institution newEntity() {
    Institution institution = new Institution();
    institution.setCode(UUID.randomUUID().toString());
    institution.setName(NAME);
    institution.setDescription(DESCRIPTION);
    institution.setHomepage(HOMEPAGE);
    institution.setAdditionalNames(Collections.emptyList());
    return institution;
  }

  @Override
  protected void assertNewEntity(Institution institution) {
    assertEquals(NAME, institution.getName());
    assertEquals(DESCRIPTION, institution.getDescription());
    assertEquals(HOMEPAGE, institution.getHomepage());
    assertTrue(institution.getAdditionalNames().isEmpty());
  }

  @Override
  protected Institution updateEntity(Institution institution) {
    institution.setCode(CODE_UPDATED);
    institution.setName(NAME_UPDATED);
    institution.setDescription(DESCRIPTION_UPDATED);
    institution.setAdditionalNames(Arrays.asList(ADDITIONAL_NAME));
    return institution;
  }

  @Override
  protected void assertUpdatedEntity(Institution institution) {
    assertEquals(CODE_UPDATED, institution.getCode());
    assertEquals(NAME_UPDATED, institution.getName());
    assertEquals(DESCRIPTION_UPDATED, institution.getDescription());
    assertEquals(1, institution.getAdditionalNames().size());
    assertNotEquals(institution.getCreated(), institution.getModified());
  }

  @Override
  protected Institution newInvalidEntity() {
    return new Institution();
  }

  @Test
  public void testSuggest() {
    Institution institution1 = newEntity();
    institution1.setCode("II");
    institution1.setName("Institution name");
    UUID key1 = institutionService.create(institution1);

    Institution institution2 = newEntity();
    institution2.setCode("II2");
    institution2.setName("Institution name2");
    UUID key2 = institutionService.create(institution2);

    assertEquals(2, institutionService.suggest("institution").size());
    assertEquals(2, institutionService.suggest("II").size());
    assertEquals(1, institutionService.suggest("II2").size());
    assertEquals(1, institutionService.suggest("name2").size());
  }

  @Test
  public void listDeletedTest() {
    Institution institution1 = newEntity();
    institution1.setCode("code1");
    institution1.setName("Institution name");
    UUID key1 = institutionService.create(institution1);

    Institution institution2 = newEntity();
    institution2.setCode("code2");
    institution2.setName("Institution name2");
    UUID key2 = institutionService.create(institution2);

    assertEquals(0, institutionService.listDeleted(PAGE.apply(5, 0L)).getResults().size());

    institutionService.delete(key1);
    assertEquals(1, institutionService.listDeleted(PAGE.apply(5, 0L)).getResults().size());

    institutionService.delete(key2);
    assertEquals(2, institutionService.listDeleted(PAGE.apply(5, 0L)).getResults().size());
  }
}
