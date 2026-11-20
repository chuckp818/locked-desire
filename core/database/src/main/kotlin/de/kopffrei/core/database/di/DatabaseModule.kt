package de.kopffrei.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.kopffrei.core.database.DatabaseMigrations
import de.kopffrei.core.database.KopfFreiDatabase
import de.kopffrei.core.database.dao.AttachmentDao
import de.kopffrei.core.database.dao.CalendarEventDao
import de.kopffrei.core.database.dao.DraftDao
import de.kopffrei.core.database.dao.JournalDao
import de.kopffrei.core.database.dao.MoodDao
import de.kopffrei.core.database.dao.NoteDao
import de.kopffrei.core.database.dao.ReminderDao
import de.kopffrei.core.database.dao.TaskDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KopfFreiDatabase =
        Room.databaseBuilder(context, KopfFreiDatabase::class.java, KopfFreiDatabase.NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides fun provideNoteDao(db: KopfFreiDatabase): NoteDao = db.noteDao()
    @Provides fun provideAttachmentDao(db: KopfFreiDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun provideJournalDao(db: KopfFreiDatabase): JournalDao = db.journalDao()
    @Provides fun provideMoodDao(db: KopfFreiDatabase): MoodDao = db.moodDao()
    @Provides fun provideTaskDao(db: KopfFreiDatabase): TaskDao = db.taskDao()
    @Provides fun provideCalendarEventDao(db: KopfFreiDatabase): CalendarEventDao = db.calendarEventDao()
    @Provides fun provideReminderDao(db: KopfFreiDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideDraftDao(db: KopfFreiDatabase): DraftDao = db.draftDao()
}
