package org.gbif.registry.identity;

import org.gbif.api.model.common.User;
import org.gbif.api.service.common.IdentityService;
import org.gbif.identity.model.UserModelMutationResult;
import org.gbif.identity.mybatis.UserMapper;
import org.gbif.registry.ws.model.UserCreation;
import org.gbif.registry.ws.security.UpdateRulesManager;

import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
public class UserTestFixture {

  public static final String USER_RESOURCE_PATH = "user";

  public static final String USERNAME = "user_12";
  public static final String ALTERNATE_USERNAME = "user_13";
  public static final String PASSWORD = "password";

  private IdentityService identityService;
  private UserMapper userMapper;

  public UserTestFixture(IdentityService identityService, UserMapper userMapper) {
    this.identityService = identityService;
    this.userMapper = userMapper;
  }

  public User prepareUser() {
    return prepareUser(generateUser());
  }

  /**
   * Utility method to prepare a user in the database.
   * @param newTestUser
   * @return
   */
  public User prepareUser(UserCreation newTestUser) {
    User userToCreate = UpdateRulesManager.applyCreate(newTestUser);
    UserModelMutationResult userCreated = identityService.create(userToCreate,
            newTestUser.getPassword());
    assertNoErrorAfterMutation(userCreated);

    Integer key = identityService.get(newTestUser.getUserName()).getKey();
    UUID challengeCode = userMapper.getChallengeCode(key);
    assertTrue("Shall confirm challengeCode " + challengeCode,
            identityService.confirmChallengeCode(key, challengeCode));

    //this is currently done in the web layer (UserResource) since we confirm the challengeCode
    //directly using the service we update it here
    identityService.updateLastLogin(key);
    return userToCreate;
  }

  /**
   * Generates a test user with username {@link #USERNAME}
   * @return
   */
  private static UserCreation generateUser() {
    return generateUser(USERNAME);
  }

  /**
   * Generates a different user on each call.
   * Thread-Safe
   *
   * @return
   */
  public static UserCreation generateUser(String username) {
    UserCreation user = new UserCreation();
    user.setUserName(username);
    user.setFirstName("Tim");
    user.setLastName("Robertson");
    user.setPassword(PASSWORD);
    user.getSettings().put("language", "en");
    user.getSettings().put("country", "dk");
    user.setEmail(username + "@gbif.org");
    return user;
  }

  private static void assertNoErrorAfterMutation(UserModelMutationResult userModelMutationResult) {
    if (userModelMutationResult.containsError()) {
      fail("Shall not contain error. Got " + userModelMutationResult.getError() + "," +
              userModelMutationResult.getConstraintViolation());
    }
  }
}