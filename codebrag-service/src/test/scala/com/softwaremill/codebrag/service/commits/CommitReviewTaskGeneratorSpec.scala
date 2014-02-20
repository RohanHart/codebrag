package com.softwaremill.codebrag.service.commits

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FlatSpec}
import akka.testkit.TestActorRef
import com.softwaremill.codebrag.domain.{LightweightCommitInfo, CommitReviewTask, NewCommitsLoadedEvent}
import org.bson.types.ObjectId
import com.softwaremill.codebrag.domain.builder.UserAssembler
import org.scalatest.mock.MockitoSugar
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.mockito.Matchers._
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import scala.util.Random
import com.softwaremill.codebrag.common.{Utils, ClockSpec}
import com.softwaremill.codebrag.dao.user.UserDAO
import com.softwaremill.codebrag.dao.commitinfo.CommitInfoDAO
import com.softwaremill.codebrag.dao.reviewtask.CommitReviewTaskDAO

class CommitReviewTaskGeneratorSpec
  extends FlatSpec with ShouldMatchers with BeforeAndAfter with BeforeAndAfterAll with MockitoSugar with ClockSpec {

  behavior of "CommitReviewTaskGenerator"

  implicit var system = ActorSystem("MyActorSystem", ConfigFactory.load("test"))
  var generator: TestActorRef[CommitReviewTaskGenerator] = _
  var userDaoMock: UserDAO = _
  var reviewTaskDaoMock: CommitReviewTaskDAO = _
  var commitInfoDaoMock: CommitInfoDAO = _
  override val fixtureTime = new DateTime(23333333).getMillis

  val repoName = "codebrag"
  val currentHEAD = "123123123"

  before {
    userDaoMock = mock[UserDAO]
    reviewTaskDaoMock = mock[CommitReviewTaskDAO]
    commitInfoDaoMock = mock[CommitInfoDAO]
    generator = TestActorRef(new CommitReviewTaskGenerator(userDaoMock, reviewTaskDaoMock, commitInfoDaoMock))
  }

  override protected def afterAll() {
    super.afterAll()
    system.shutdown()
    system.awaitTermination()
  }

  it should "generate 22 tasks when there are two non-author users and 11 new commits" in {
    // given
    val commits = randomCommits(count = 11)
    val users = List(
      user(name = "Author Name"),
      user(name = "Sofokles Smart"),
      user(name = "Bruce Angry"))
    given(userDaoMock.findAll()).willReturn(users)

    // when
    generator ! NewCommitsLoadedEvent(firstTime = false, repoName, currentHEAD, commits)

    // then
    verify(reviewTaskDaoMock, times(22)).save(any[CommitReviewTask])
  }

  it should "generate 0 tasks when the only user is commit's author" in {
    // given
    val commits = randomCommits(count = 11)
    val users = List(user(name = "Author Name"))
    given(userDaoMock.findAll()).willReturn(users)

    // when
    generator ! NewCommitsLoadedEvent(firstTime = false, repoName, currentHEAD, commits)

    // then
    verify(reviewTaskDaoMock, never()).save(any[CommitReviewTask])
  }

  it should "generate only limited number of tasks for each user on first update" in {
    // given
    val commits = randomCommits(count = CommitReviewTaskGeneratorActions.LastCommitsToReviewCount + Random.nextInt(500))
    val userWhoShouldNotReceiveTasks =  user(name = "Author Name")
    val users = List(
      userWhoShouldNotReceiveTasks,
      user(name = "Sofokles Smart"),
      user(name = "Bruce Angry"))
    given(userDaoMock.findAll()).willReturn(users)

    // when
    generator ! NewCommitsLoadedEvent(firstTime = true, repoName, currentHEAD, commits)

    // then
    verify(reviewTaskDaoMock, times(2 * CommitReviewTaskGeneratorActions.LastCommitsToReviewCount)).save(any[CommitReviewTask])
  }

  it should "generate only limited number of tasks on first update if there are less commits than limit" in {
    // given
    val commitCount = CommitReviewTaskGeneratorActions.LastCommitsToReviewCount - 2
    val commits = randomCommits(count = commitCount)
    val userWhoShouldNotReceiveTasks =  user(name = "Author Name")
    val users = List(
      userWhoShouldNotReceiveTasks,
      user(name = "Sofokles Smart"),
      user(name = "Bruce Angry"))
    given(userDaoMock.findAll()).willReturn(users)

    // when
    generator ! NewCommitsLoadedEvent(firstTime = true, repoName, currentHEAD, commits)

    // then
    verify(reviewTaskDaoMock, times(2 * commitCount)).save(any[CommitReviewTask])
  }
  it should "generate tasks only for non-author when commits are updated" in {
    // given
    val commits = randomCommits(count = 1)
    val sofokles = user(name = "Sofokles Smart")
    val bruce  = user(name = "Bruce Angry")
    val commitAuthor = user(name = "Author Name")
    val users = List(
      commitAuthor,
      sofokles,
      bruce)
    given(userDaoMock.findAll()).willReturn(users)

    // when
    generator ! NewCommitsLoadedEvent(firstTime = false, repoName, currentHEAD, commits)

    // then
    verify(reviewTaskDaoMock).save(CommitReviewTask(commits(0).id, sofokles.id))
    verify(reviewTaskDaoMock).save(CommitReviewTask(commits(0).id, bruce.id))
    verify(reviewTaskDaoMock, never()).save(CommitReviewTask(commits(0).id, commitAuthor.id))

  }

  private def user(name: String) = {
    UserAssembler.randomUser.withFullName(name).get
  }

  private def randomCommits(count: Int, date: DateTime = new DateTime()) = {
    val authorEmail = "author@example.org"
    val fakeSha = Utils.sha1(authorEmail)
    List.fill(count)(LightweightCommitInfo(new ObjectId(), fakeSha, "Author Name", authorEmail, date))
  }

}

