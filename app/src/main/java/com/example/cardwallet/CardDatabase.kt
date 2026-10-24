package com.example.cardwallet

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// 1. Модель карточки (Таблица в базе данных)
@Entity(tableName = "cards")
data class DiscountCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,          // Уникальный номер карты (создаётся сам)
    val storeName: String,    // Название магазина (например, "Пятёрочка" или "Magnum")
    val codeValue: String,    // Цифры или текст штрих-кода
    val codeFormat: Int,      // Тип кода (QR-код, обычный штрих-код и т.д.)
    val colorHex: Long        // Цвет карточки на экране
)

// 2. Пульт управления базой (DAO — команды сохранения, чтения и удаления)
@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY id DESC")
    fun getAllCards(): Flow<List<DiscountCard>> // Следит за списком карт в реальном времени

    @Insert
    suspend fun insertCard(card: DiscountCard) // Сохранить новую карту

    @Delete
    suspend fun deleteCard(card: DiscountCard) // Удалить карту
}

// 3. Сама база данных
@Database(entities = [DiscountCard::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "card_wallet_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}