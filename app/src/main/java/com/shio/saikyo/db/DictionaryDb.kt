package com.shio.saikyo.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

object DictionaryDbTable {
    @Entity(tableName = "lemma")
    data class Lemma(
        @PrimaryKey @ColumnInfo(name = "lemma_id") val lemmaId: Int,
        @ColumnInfo(name = "seq_no") val seqNo: Int,
        @ColumnInfo(name = "kanji") val kanji: String,
        @ColumnInfo(name = "kana") val kana: String,
        @ColumnInfo(name = "meaning") val meaning: String?,
    )

    @Entity(tableName = "lexicon", primaryKeys = ["lemma_id", "term"])
    data class Term(
        val term: String,
        @ColumnInfo(name = "lemma_id") val lemmaId: Int
    )

    @Entity(tableName = "antonym", primaryKeys = ["lemma1_id", "ant"])
    data class Antonym(
        @ColumnInfo(name = "lemma1_id") val lemmaId1: Int,
        @ColumnInfo(name = "lemma2_id") val lemmaId2: Int?,
        val ant: String
    )

    @Entity(tableName = "example_lemma", primaryKeys = ["example_id", "lemma_id"])
    data class ExampleLemma(
        @ColumnInfo(name = "example_id") val exampleId: Int,
        @ColumnInfo(name = "lemma_id") val lemmaId: Int,
    )

    @Entity(tableName = "example")
    data class Example(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "example_id")
        val exampleId: Int,

        val example: String
    )

    @Entity(tableName = "synonym", primaryKeys = ["lemma1_id", "syn"])
    data class Synonym(
        @ColumnInfo(name = "lemma1_id") val lemmaId1: Int,
        @ColumnInfo(name = "lemma2_id") val lemmaId2: Int?,
        val syn: String
    )
}

data class LemmaEntry(
    @ColumnInfo(name = "lemma_id") val lemmaId: Int,
    val kanji: String,
    val kana: String,
    val meaning: String?
)

data class WordInfo(
    val word: String,
    val reading: String,
    val meaning: String,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val examples: List<String>
)

@Dao
interface LemmaDAO {
    // TODO: change the data model to make this more efficient
    @Query(
        """
        update lemma
        set meaning = :lemmaMeaning
        where lemma_id = :lemmaId
    """
    )
    suspend fun updateLemmaMeaning(lemmaId: Int, lemmaMeaning: String)

    @Query(
        """
        update lemma
        set meaning = null
        where lemma_id = :lemmaId
    """
    )
    suspend fun updateLemmaMeaning(lemmaId: Int)

    @Query(
        """
        select distinct
            lemma.lemma_id,
            lemma.kanji,
            lemma.kana,
            lemma.meaning
        from (
            select
                lemma_id,
                case when length(term) = length(:term) then 0 else 1 end exact_match
            from lexicon
            where term like '%' || :term || '%'
        ) as lex
        join lemma on lex.lemma_id = lemma.lemma_id
        order by lex.exact_match
    """
    )
    fun getEntriesMatchingPaged(term: String): PagingSource<Int, LemmaEntry>

    @Query(
        """
        select * from lemma where lemma_id = :lemmaId
    """
    )
    suspend fun getAllInfo(lemmaId: Int): DictionaryDbTable.Lemma
}

@Dao
abstract class WordDAO {
    @Query("update lemma set meaning = :meaning where lemma_id = :lemmaId")
    abstract suspend fun updateMeaning(lemmaId: Int, meaning: String? = null)

    @Insert
    abstract suspend fun insertSynonyms(syns: List<DictionaryDbTable.Synonym>)

    @Query("delete from synonym where lemma1_id = :lemmaId")
    abstract suspend fun removeSynonyms(lemmaId: Int)

    @Query("delete from antonym where lemma1_id = :lemmaId")
    abstract suspend fun removeAntonyms(lemmaId: Int)

    @Query("delete from example_lemma where lemma_id = :lemmaId")
    abstract suspend fun removeLemmaLinkFromExample(lemmaId: Int)

    @Insert
    abstract suspend fun insertAntonyms(syns: List<DictionaryDbTable.Antonym>)

    @Query("insert into example(example) values(:example)")
    abstract suspend fun insertExample(example: String): Long

    @Insert
    abstract suspend fun insertLemmaExampleLink(links: List<DictionaryDbTable.ExampleLemma>)

    @Query("select syn from synonym where lemma1_id = :lemmaId")
    abstract suspend fun getSynonyms(lemmaId: Int): List<String>

    @Query("select ant from antonym where lemma1_id = :lemmaId ")
    abstract suspend fun getAntonyms(lemmaId: Int): List<String>

    @Query("""
        select
            e.example
        from example_lemma as el
        join example as e on e.example_id = el.example_id
        where el.lemma_id = :lemmaId
    """)
    abstract suspend fun getExamples(lemmaId: Int): List<String>

    @Query("select * from lemma where lemma_id = :lemmaId")
    abstract suspend fun getLemma(lemmaId: Int): DictionaryDbTable.Lemma

    @Transaction
    open suspend fun updateLemma(lemmaId: Int, info: WordInfo) {
        updateMeaning(lemmaId, info.meaning)
        insertSynonyms(info.synonyms.map { DictionaryDbTable.Synonym(lemmaId, null, it) })
        insertAntonyms(info.antonyms.map { DictionaryDbTable.Antonym(lemmaId, null, it) })
        val exIds = info.examples.map { insertExample(it).toInt() }

        insertLemmaExampleLink(exIds.map { DictionaryDbTable.ExampleLemma(it, lemmaId) })
    }

    @Transaction
    open suspend fun updateLemma(lemmaId: Int) {
        updateMeaning(lemmaId)
        removeSynonyms(lemmaId)
        removeAntonyms(lemmaId)
        removeLemmaLinkFromExample(lemmaId)
    }

    @Transaction
    open suspend fun getAllInfo(lemmaId: Int): WordInfo {
        val lemma = getLemma(lemmaId)
        val syns = getSynonyms(lemmaId)
        val ants = getAntonyms(lemmaId)
        val examples = getExamples(lemmaId)

        return WordInfo(
            lemma.kanji,
            lemma.kana,
            lemma.meaning!!,
            syns,
            ants,
            examples
        )
    }
}

@Database(
    entities = [
        DictionaryDbTable.Lemma::class,
        DictionaryDbTable.Term::class,
        DictionaryDbTable.Antonym::class,

        DictionaryDbTable.Example::class,
        DictionaryDbTable.ExampleLemma::class,
        DictionaryDbTable.Synonym::class
    ],

    version = 3,
)
abstract class DictionaryDb : RoomDatabase() {
    abstract fun lemmas(): LemmaDAO

    abstract fun words(): WordDAO

    companion object {
        @Volatile
        private var instance: DictionaryDb? = null

        fun getDatabase(ctx: Context): DictionaryDb {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return instance ?: synchronized(this) {
                instance = Room.databaseBuilder(
                    ctx.applicationContext,
                    DictionaryDb::class.java,
                    "ddb.sqlite3"
                )
                    .fallbackToDestructiveMigration() // TODO: proper migrations
                    .createFromAsset("db/lemmas_v1.db")
                    .build()

                return@synchronized instance!!
            }
        }
    }
}
