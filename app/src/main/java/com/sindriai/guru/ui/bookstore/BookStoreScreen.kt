package com.sindriai.guru.ui.bookstore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sindriai.guru.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookStoreScreen(
    onBack: () -> Unit = {},
    onOpenCourse: (courseId: String) -> Unit = {}
) {

    val books = remember {
        listOf(
            DummyBook(
                "B005", "Class 10 Biology", "Biology| Science • CBSE Pattern",
                4.6f, 32, 120, 60, "Biology", R.drawable.class_10_biology
            ),

            DummyBook(
                    "B006", "Class 10 Physics", "Physics| Science • CBSE Pattern",
            0.0f, 0, 120, 60, "Physics", R.drawable.class_10_physics
            ),
            DummyBook(
                "B007", "Class 10 Chemistry", "Chemistry| Science • CBSE Pattern",
                0.0f, 0, 120, 60, "Chemistry", R.drawable.class_10_chemistry
            )
        )
    }

    // Only first book purchased
    val purchasedDownloadedIds = remember { setOf("B001") }

    // Upcoming (disabled) books
    val upcomingIds = remember { setOf("B006", "B007") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Store") },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(books) { book ->
                val isPurchased = purchasedDownloadedIds.contains(book.id)
                val isUpcoming = upcomingIds.contains(book.id)

                GridBookCard(
                    book = book,
                    isPurchased = isPurchased,
                    isUpcoming = isUpcoming,
                    onClick = { onOpenCourse(book.id) }
                )
            }
        }
    }
}

/* ---------------- Grid Card ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridBookCard(
    book: DummyBook,
    isPurchased: Boolean,
    isUpcoming: Boolean,
    onClick: () -> Unit
) {
    val cardAlpha = if (isUpcoming) 0.55f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .alpha(cardAlpha),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        enabled = !isUpcoming,      // ✅ disables click + ripple
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Image(
                painter = painterResource(book.imageRes),
                contentDescription = "Cover",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(0.9f),
                contentScale = ContentScale.Crop
            )

            Text(
                text = book.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            /*Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "⭐ ${book.rating}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "(${book.reviewCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }*/

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubjectTag(book.subject)

                when {
                    isPurchased -> PurchasedTag()
                    isUpcoming -> UpcomingTag()
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isUpcoming) "Coming Soon" else "For Basic Members Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- Tags ---------------- */

@Composable
private fun PurchasedTag() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF2E7D32) // Green background
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            text = "Purchased",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun UpcomingTag() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            text = "Upcoming",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubjectTag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/* ---------------- Model ---------------- */

private data class DummyBook(
    val id: String,
    val title: String,
    val subtitle: String,
    val rating: Float,
    val reviewCount: Int,
    val originalPrice: Int,
    val discountPrice: Int,
    val subject: String,
    val imageRes: Int
)
