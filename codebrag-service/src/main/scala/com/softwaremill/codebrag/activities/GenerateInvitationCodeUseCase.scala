package com.softwaremill.codebrag.activities

import org.bson.types.ObjectId
import com.softwaremill.codebrag.service.invitations.InvitationService
import com.softwaremill.codebrag.dao.user.UserDAO
import com.softwaremill.codebrag.activities.assertions.UserAssertions

class GenerateInvitationCodeUseCase(invitationService: InvitationService, implicit protected val userDao: UserDAO) {

  import UserAssertions._

  def execute(userId: ObjectId): String = {
    assertUserWithId(userId, mustBeAdmin)
    invitationService.generateInvitationCode(userId)
  }

}
