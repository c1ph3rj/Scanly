package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A tag on a document. Rows go with the document (permanent delete) or the tag; trashing a
 * document keeps them so a restore brings its tags back.
 */
@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // The primary key already leads with documentId; Room still wants an index named for each
        // foreign key column, and it keeps document deletes from scanning the table.
        Index(value = ["documentId"]),
        Index(value = ["tagId"]),
    ],
)
data class DocumentTagEntity(
    val documentId: String,
    val tagId: String,
)
