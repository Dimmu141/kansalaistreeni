package fi.kansalaisuuskoe

import android.app.Application
import android.content.Context
import androidx.room.Room
import fi.kansalaisuuskoe.data.*

class AppContainer(context: Context) {
    internal val database = Room.databaseBuilder(context, LearningDatabase::class.java, "learning.db")
        .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4, LearningDatabase.MIGRATION_4_5, LearningDatabase.MIGRATION_5_6, LearningDatabase.MIGRATION_6_7).build()
    val learning = LearningRepository(database)
    val assessments = AssessmentRepository(database)
    val settings = SettingsRepository(context)
}
class CitizenshipApplication : Application() {
    val container by lazy { AppContainer(this) }
}
