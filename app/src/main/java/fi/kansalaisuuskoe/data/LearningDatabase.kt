package fi.kansalaisuuskoe.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "topics")
data class TopicEntity(@PrimaryKey val id: String, val title: String, val description: String, val sortOrder: Int,
    @ColumnInfo(defaultValue = "'{}'") val translations: String = "{}",
    @ColumnInfo(defaultValue = "'{}'") val lessons: String = "{}",
) {
    fun model() = dto().model()
    fun dto() = TopicDto(id, title, description, sortOrder, requireNotNull(ContentCodec.topicTranslationsAdapter.fromJson(translations)), requireNotNull(ContentCodec.lessonsAdapter.fromJson(lessons)))
}
@Entity(tableName = "questions")
data class QuestionEntity(@PrimaryKey val id: String, val revision: Int, val payload: String)
@Entity(tableName = "content_metadata")
data class ContentMetadata(@PrimaryKey val slot: Int = 1, val packageId: String, val version: Int, val hash: String)
@Entity(tableName = "learning_rules")
data class LearningRulesEntity(@PrimaryKey val slot: Int = 1, val version: Int)
@Entity(tableName = "attempts", indices = [Index("questionId"), Index("answeredAt")])
data class AttemptEntity(
    @PrimaryKey val id: String, val questionId: String, val revision: Int,
    val topicId: String, val selectedOptionId: String, val correct: Boolean, val answeredAt: Long,
    @ColumnInfo(defaultValue = "'fi'") val language: String = "fi",
) {
    fun model() = QuestionAttempt(id, questionId, revision, topicId, selectedOptionId, correct, answeredAt, language)
}
@Entity(tableName = "question_progress")
data class ProgressEntity(
    @PrimaryKey val questionId: String, val revision: Int, val attempts: Int,
    val correctAnswers: Int, val consecutiveCorrect: Int, val mastery: Int,
    val lastAnswered: Long?, val nextReview: Long?,
) {
    fun model() = UserQuestionProgress(questionId, revision, attempts, correctAnswers, consecutiveCorrect, mastery, lastAnswered, nextReview)
    companion object {
        fun from(p: UserQuestionProgress) = ProgressEntity(p.questionId, p.revision, p.attempts, p.correctAnswers, p.consecutiveCorrect, p.mastery, p.lastAnswered, p.nextReview)
    }
}
@Dao
interface LearningDao {
    @Query("SELECT * FROM topics ORDER BY sortOrder, rowid") fun observeTopics(): Flow<List<TopicEntity>>
    @Query("SELECT * FROM topics ORDER BY sortOrder, rowid") suspend fun topics(): List<TopicEntity>
    @Query("SELECT * FROM questions ORDER BY rowid") fun observeQuestions(): Flow<List<QuestionEntity>>
    @Query("SELECT * FROM question_progress") fun observeProgress(): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM attempts ORDER BY answeredAt, rowid") fun observeAttempts(): Flow<List<AttemptEntity>>
    @Query("SELECT * FROM attempts ORDER BY answeredAt, rowid") suspend fun attempts(): List<AttemptEntity>
    @Query("SELECT * FROM attempts WHERE questionId = :id AND revision = :revision ORDER BY answeredAt, rowid") suspend fun questionAttempts(id: String, revision: Int): List<AttemptEntity>
    @Query("SELECT * FROM questions WHERE id = :id") suspend fun question(id: String): QuestionEntity?
    @Query("SELECT * FROM questions ORDER BY rowid") suspend fun questions(): List<QuestionEntity>
    @Query("SELECT * FROM question_progress WHERE questionId = :id") suspend fun progress(id: String): ProgressEntity?
    @Query("SELECT * FROM attempts WHERE id = :id") suspend fun attempt(id: String): AttemptEntity?
    @Query("SELECT * FROM content_metadata WHERE slot = 1") suspend fun metadata(): ContentMetadata?
    @Query("SELECT version FROM learning_rules WHERE slot = 1") suspend fun rulesVersion(): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRules(rules: LearningRulesEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTopics(topics: List<TopicEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuestions(questions: List<QuestionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMetadata(metadata: ContentMetadata)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttempt(attempt: AttemptEntity): Long
    @Insert suspend fun insertAttempts(attempts: List<AttemptEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(progress: ProgressEntity)
    @Query("DELETE FROM questions") suspend fun deleteQuestions()
    @Query("DELETE FROM topics") suspend fun deleteTopics()
    @Query("DELETE FROM question_progress") suspend fun deleteProgress()
    @Query("DELETE FROM attempts") suspend fun deleteAttempts()
}
@Database(entities = [TopicEntity::class, QuestionEntity::class, ContentMetadata::class, AttemptEntity::class, ProgressEntity::class, LearningRulesEntity::class, AssessmentEntity::class], version = 6, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    abstract fun assessmentDao(): AssessmentDao
    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `topics` ADD COLUMN `lessons` TEXT NOT NULL DEFAULT '{}'")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `assessments` (`id` TEXT NOT NULL, `language` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'fi'")
                // Old imported Swedish content was possible through the internal importer.
                db.query("SELECT `id`, `payload` FROM `questions`").use { cursor ->
                    while (cursor.moveToNext()) {
                        if (org.json.JSONObject(cursor.getString(1)).optString("language") == "sv") {
                            db.execSQL("UPDATE `attempts` SET `language` = 'sv' WHERE `questionId` = ?", arrayOf(cursor.getString(0)))
                        }
                    }
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `learning_rules` (`slot` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`slot`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `topics` ADD COLUMN `translations` TEXT NOT NULL DEFAULT '{}'")
            }
        }
    }
}
