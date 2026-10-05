package com.example.kanjidb.data.user

import androidx.room.*

@Entity(tableName = "custom_list")
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val manualIndex: Long
)

@Entity(
    tableName = "custom_list_kanji",
    primaryKeys = ["listId", "character"],
    foreignKeys = [ForeignKey(entity = CustomListEntity::class,
        parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")]
)
data class CustomListKanjiEntity(val listId: Long, val character: String, val manualIndex: Long)

data class CustomListWithKanji(
    @Embedded val list: CustomListEntity,
    @Relation(parentColumn = "id", entityColumn = "listId")
    val memberships: List<CustomListKanjiEntity>
) {
    val characters: List<String> get() = memberships.sortedWith(
        compareBy<CustomListKanjiEntity> { it.manualIndex }.thenBy { it.character }
    ).map { it.character }
}
