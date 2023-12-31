package com.thebrownfoxx.books.realm

import com.thebrownfoxx.books.model.BookType
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import java.time.LocalDate

class BookRealmDatabase {
    private val config = RealmConfiguration
        .Builder(schema = setOf(RealmBook::class))
        .schemaVersion(2)
        .build()
    private val realm = Realm.open(config)

    fun getBook(id: ObjectId) =
        realm.query<RealmBook>("id == $0", id).first().asFlow().map { it.obj }

    fun getAllNonFavoriteBooks() =
        realm.query<RealmBook>("type == $0", BookType.NonFavorite.name).asFlow().map { it.list }

    fun getAllFavoriteBooks() =
        realm.query<RealmBook>("type == $0", BookType.Favorite.name).asFlow().map { it.list }

    fun getAllArchivedBooks() =
        realm.query<RealmBook>("type == $0", BookType.Archived.name).asFlow().map { it.list }

    suspend fun addBook(
        title: String,
        author: String,
        datePublished: LocalDate,
        pages: Int,
    ) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = RealmBook()
                book.title = title
                book.author = author
                book.datePublishedEpochDay = datePublished.toEpochDay()
                book.dateAddedEpochDay = LocalDate.now().toEpochDay()
                book.pages = pages
                copyToRealm(book)
            }
        }
    }

    suspend fun updateBook(
        id: ObjectId,
        title: String? = null,
        author: String? = null,
        datePublished: LocalDate? = null,
        pages: Int? = null,
    ) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) {
                    val latestBook = findLatest(book)
                    if (title != null) latestBook?.title = title
                    if (author != null) latestBook?.author = author
                    if (datePublished != null)
                        latestBook?.datePublishedEpochDay = datePublished.toEpochDay()
                    if (pages != null) {
                        latestBook?.pages = pages
                        if ((latestBook?.pagesRead ?: 0) > pages) {
                            latestBook?.pagesRead = pages
                        }
                    }

                    val hasEdits = listOf(author, title, datePublished, pages).any { it != null }
                    if (hasEdits) latestBook?.dateModifiedEpochDay = LocalDate.now().toEpochDay()
                }
            }
        }
    }

    suspend fun updateBookProgress(
        id: ObjectId,
        pagesRead: Int,
    ) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) {
                    findLatest(book)?.pagesRead = pagesRead
                }
            }
        }
    }

    suspend fun favoriteBook(id: ObjectId) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) { findLatest(book)?.type = BookType.Favorite.name }
            }
        }
    }

    suspend fun unfavoriteBook(id: ObjectId) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) { findLatest(book)?.type = BookType.NonFavorite.name }
            }
        }
    }

    suspend fun archiveBook(id: ObjectId) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) {
                    findLatest(book)?.type = BookType.Archived.name
                }
            }
        }
    }

    suspend fun unarchiveBook(id: ObjectId) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = realm.query<RealmBook>("id == $0", id).first().find()
                if (book != null) {
                    findLatest(book)?.type = BookType.NonFavorite.name
                }
            }
        }
    }

    suspend fun deleteBook(id: ObjectId) {
        withContext(Dispatchers.IO) {
            realm.write {
                val book = query<RealmBook>("id == $0", id).first().find()
                if (book != null) delete(findLatest(book)!!)
            }
        }
    }

    suspend fun unarchiveAll() {
        withContext(Dispatchers.IO) {
            realm.write {
                val books = realm.query<RealmBook>("type == $0", BookType.Archived.name).find()
                for (book in books) {
                    findLatest(book)?.type = BookType.NonFavorite.name
                }
            }
        }
    }

    suspend fun deleteAllArchived() {
        withContext(Dispatchers.IO) {
            realm.write {
                val books = realm.query<RealmBook>("type == $0", BookType.Archived.name).find()
                for (book in books) {
                    delete(findLatest(book)!!)
                }
            }
        }
    }
}