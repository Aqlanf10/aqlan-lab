package com.aqlanlab.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.InventoryItem
import com.aqlanlab.app.data.models.InventoryTransaction
import com.aqlanlab.app.data.models.InventoryTransactionType
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
  viewModel: DentalLabViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()
  val currency by viewModel.currency.collectAsState()
  val allItems by viewModel.allInventoryItems.collectAsState()
  val lowStockItems by viewModel.lowStockInventoryItems.collectAsState()
  val lowStockCount by viewModel.lowStockCount.collectAsState()
  val filteredItems by viewModel.filteredInventoryItems.collectAsState()
  val transactions by viewModel.allInventoryTransactions.collectAsState()
  val searchQuery by viewModel.inventorySearchQuery.collectAsState()
  val categoryFilter by viewModel.inventoryCategoryFilter.collectAsState()
  val stockFilter by viewModel.inventoryStockFilter.collectAsState()

  var showAddEditDialog by remember { mutableStateOf(false) }
  var itemToEdit by remember { mutableStateOf<InventoryItem?>(null) }
  var showAdjustStockDialog by remember { mutableStateOf(false) }
  var itemToAdjust by remember { mutableStateOf<InventoryItem?>(null) }
  var showReorderDialog by remember { mutableStateOf(false) }
  var itemToReorder by remember { mutableStateOf<InventoryItem?>(null) }
  var showTransactionsHistorySheet by remember { mutableStateOf(false) }
  var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }

  val categories = listOf(
    "الكل",
    "مواد الطبعات",
    "الخزف والزركونيا",
    "الجبس والشمع",
    "الأكريليك والتعويضات",
    "المواد اللاصقة والاستهلاكيات"
  )

  val totalInventoryValue = remember(allItems) {
    allItems.sumOf { it.totalValue }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "المخزون والمواد السنية",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "متابعة أرصدة المواد وتنبيهات النواقص",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          // Low Stock indicator chip
          if (lowStockCount > 0) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier
                .clickable {
                  viewModel.setInventoryStockFilter(if (stockFilter == "LOW_STOCK") "ALL" else "LOW_STOCK")
                }
                .testTag("inventory_low_stock_counter_btn")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  Icons.Default.Warning,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = "$lowStockCount نواقص",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }
            }
          }

          // Transactions log button
          IconButton(
            onClick = { showTransactionsHistorySheet = true },
            modifier = Modifier.testTag("inventory_history_btn")
          ) {
            Icon(Icons.Default.History, contentDescription = "سجل حركات المخزون")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      if (activeUser.role != UserRole.STAFF) {
        ExtendedFloatingActionButton(
          onClick = {
            itemToEdit = null
            showAddEditDialog = true
          },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("إضافة مادة") },
          modifier = Modifier.testTag("add_inventory_item_fab")
        )
      }
    },
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp)
    ) {
      // 1. KPI Summary Cards
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Total items
          Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("إجمالي المواد", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
              Text(
                text = "${allItems.size}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
              Text("صنف مسجل", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
          }

          // Low Stock Alert Box
          Card(
            modifier = Modifier
              .weight(1.2f)
              .clickable {
                viewModel.setInventoryStockFilter(if (stockFilter == "LOW_STOCK") "ALL" else "LOW_STOCK")
              },
            colors = CardDefaults.cardColors(
              containerColor = if (lowStockCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lowStockCount > 0) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Text(
                  text = "نواقص المخزون",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else Color.Gray,
                  fontWeight = FontWeight.Bold
                )
              }
              Text(
                text = "$lowStockCount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (lowStockCount > 0) "بحاجة لإعادة طلب!" else "كافة المواد متوفرة",
                style = MaterialTheme.typography.labelSmall,
                color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else Color.Gray
              )
            }
          }

          // Total valuation
          if (activeUser.role != UserRole.STAFF) {
            Card(
              modifier = Modifier.weight(1.2f),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text("قيمة المخزون", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                  text = "%.0f $currency".format(totalInventoryValue),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text("تقييم الرصيد", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
              }
            }
          }
        }
      }

      // 2. Low Stock Warning Alert Banner (if any)
      if (lowStockCount > 0) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("low_stock_notification_banner")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "تنبيه نقص المخزون السني ($lowStockCount مواد)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.error
                )
                Text(
                  text = "المواد الموضحة أدناه وصلت إلى الحد الأدنى أو نفدت، يرجى إعادة الطلب من الموردين.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }

              Button(
                onClick = {
                  viewModel.setInventoryStockFilter("LOW_STOCK")
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("filter_low_stock_btn")
              ) {
                Text("عرض", fontSize = 12.sp)
              }
            }
          }
        }
      }

      // 3. Search and Filters
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Search Bar
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setInventorySearchQuery(it) },
            placeholder = { Text("بحث عن مادة، مورد، أو مكان التخزين...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.setInventorySearchQuery("") }) {
                  Icon(Icons.Default.Clear, contentDescription = "مسح")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("inventory_search_input")
          )

          // Stock Status Chips (All vs Low Stock vs In Stock)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = stockFilter == "ALL",
              onClick = { viewModel.setInventoryStockFilter("ALL") },
              label = { Text("كافة المواد (${allItems.size})") },
              leadingIcon = {
                if (stockFilter == "ALL") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier = Modifier.testTag("filter_all_chip")
            )

            FilterChip(
              selected = stockFilter == "LOW_STOCK",
              onClick = { viewModel.setInventoryStockFilter("LOW_STOCK") },
              label = {
                Text("⚠️ النواقص ($lowStockCount)")
              },
              leadingIcon = {
                if (stockFilter == "LOW_STOCK") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
              ),
              modifier = Modifier.testTag("filter_low_stock_chip")
            )

            FilterChip(
              selected = stockFilter == "IN_STOCK",
              onClick = { viewModel.setInventoryStockFilter("IN_STOCK") },
              label = { Text("✅ متوفر (${allItems.count { !it.isLowStock }})") },
              leadingIcon = {
                if (stockFilter == "IN_STOCK") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier = Modifier.testTag("filter_in_stock_chip")
            )
          }

          // Category Scrollable Chips
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            categories.forEach { cat ->
              val isSelected = categoryFilter == cat
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable { viewModel.setInventoryCategoryFilter(cat) }
              ) {
                Text(
                  text = cat,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }

      // 4. Inventory Items List
      if (filteredItems.isEmpty()) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
              Text("لا توجد مواد مطابقة للبحث أو التصفية", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
              Text("يمكنك تغيير كلمات البحث أو إضافة مادة جديدة للمخزون.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
            }
          }
        }
      } else {
        items(filteredItems, key = { it.id }) { item ->
          InventoryItemCard(
            item = item,
            currency = currency,
            userRole = activeUser.role,
            onQuickAdd = {
              viewModel.quickRestockItem(item, 1.0)
              Toast.makeText(context, "تمت إضافة 1 ${item.unit} إلى رصيد ${item.name}", Toast.LENGTH_SHORT).show()
            },
            onQuickConsume = {
              if (item.currentStock > 0) {
                viewModel.quickConsumeItem(item, 1.0)
                Toast.makeText(context, "تم صرف 1 ${item.unit} من ${item.name}", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, "الرصيد 0 لا يمكن الصرف!", Toast.LENGTH_SHORT).show()
              }
            },
            onAdjustStock = {
              itemToAdjust = item
              showAdjustStockDialog = true
            },
            onReorder = {
              itemToReorder = item
              showReorderDialog = true
            },
            onEdit = {
              itemToEdit = item
              showAddEditDialog = true
            },
            onDelete = {
              itemToDelete = item
            }
          )
        }
      }
    }

    // --- Dialogs ---

    // 1. Add / Edit Item Dialog
    if (showAddEditDialog) {
      AddEditInventoryItemDialog(
        initialItem = itemToEdit,
        categories = categories.filter { it != "الكل" },
        onDismiss = {
          showAddEditDialog = false
          itemToEdit = null
        },
        onSave = { newItem ->
          if (itemToEdit == null) {
            viewModel.addInventoryItem(newItem) {
              Toast.makeText(context, "تمت إضافة المادة بنجاح", Toast.LENGTH_SHORT).show()
            }
          } else {
            viewModel.updateInventoryItem(newItem) {
              Toast.makeText(context, "تم تحديث بيانات المادة بنجاح", Toast.LENGTH_SHORT).show()
            }
          }
          showAddEditDialog = false
          itemToEdit = null
        }
      )
    }

    // 2. Adjust Stock / Restock Dialog
    if (showAdjustStockDialog && itemToAdjust != null) {
      val target = itemToAdjust!!
      AdjustStockDialog(
        item = target,
        onDismiss = {
          showAdjustStockDialog = false
          itemToAdjust = null
        },
        onConfirm = { amount, type, reason ->
          viewModel.adjustInventoryStock(
            itemId = target.id,
            quantityChange = amount,
            type = type,
            reason = reason
          ) {
            Toast.makeText(context, "تم تحديث الرصيد وتسجيل الحركة بنجاح", Toast.LENGTH_SHORT).show()
          }
          showAdjustStockDialog = false
          itemToAdjust = null
        }
      )
    }

    // 3. Reorder via WhatsApp Dialog
    if (showReorderDialog && itemToReorder != null) {
      val target = itemToReorder!!
      ReorderItemDialog(
        item = target,
        onDismiss = {
          showReorderDialog = false
          itemToReorder = null
        },
        onSendOrder = { orderQty ->
          viewModel.shareSupplierOrderViaWhatsApp(context, target, orderQty)
          showReorderDialog = false
          itemToReorder = null
        },
        onCallSupplier = {
          if (target.supplierPhone.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${target.supplierPhone}"))
            context.startActivity(intent)
          } else {
            Toast.makeText(context, "لا يوجد رقم هاتف مسجل للمورد", Toast.LENGTH_SHORT).show()
          }
        }
      )
    }

    // 4. Delete Confirmation Dialog
    if (itemToDelete != null) {
      val item = itemToDelete!!
      AlertDialog(
        onDismissRequest = { itemToDelete = null },
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("تأكيد حذف المادة") },
        text = { Text("هل أنت متأكد من حذف (${item.name}) من سجل المخزون نهائياً؟") },
        confirmButton = {
          Button(
            onClick = {
              viewModel.deleteInventoryItem(item) {
                Toast.makeText(context, "تم حذف المادة", Toast.LENGTH_SHORT).show()
              }
              itemToDelete = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("حذف")
          }
        },
        dismissButton = {
          TextButton(onClick = { itemToDelete = null }) {
            Text("إلغاء")
          }
        }
      )
    }

    // 5. Transactions Movement Log Bottom Sheet
    if (showTransactionsHistorySheet) {
      ModalBottomSheet(
        onDismissRequest = { showTransactionsHistorySheet = false }
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "سجل حركات المخزون (Log)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Text(
                text = "${transactions.size} حركة مسجلة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          if (transactions.isEmpty()) {
            Text(
              text = "لا توجد حركات مخزون مسجلة حتى الآن.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.Gray,
              modifier = Modifier.padding(vertical = 24.dp)
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(transactions) { tx ->
                TransactionCardItem(tx = tx)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun InventoryItemCard(
  item: InventoryItem,
  currency: String,
  userRole: UserRole,
  onQuickAdd: () -> Unit,
  onQuickConsume: () -> Unit,
  onAdjustStock: () -> Unit,
  onReorder: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (item.isLowStock) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
      } else {
        MaterialTheme.colorScheme.surface
      }
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("inventory_item_${item.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top row: Name, Category, Menu
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(Modifier.height(3.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
              Text(
                text = item.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }

            if (item.location.isNotEmpty()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                Text(
                  text = item.location,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.Gray
                )
              }
            }
          }
        }

        // Status Badge & Menu
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          if (item.isOutOfStock) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.error
            ) {
              Text(
                text = "نفد المخزون!",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          } else if (item.isLowStock) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.errorContainer
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                Text(
                  text = "نقص رصيد",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }
            }
          } else {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFDCFCE7) // Light green
            ) {
              Text(
                text = "متوفر",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF166534),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
            }
            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false }
            ) {
              DropdownMenuItem(
                text = { Text("طلب توريد (WhatsApp)") },
                leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                onClick = {
                  showMenu = false
                  onReorder()
                }
              )
              DropdownMenuItem(
                text = { Text("تعديل رصيد مخصص") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                onClick = {
                  showMenu = false
                  onAdjustStock()
                }
              )
              if (userRole != UserRole.STAFF) {
                DropdownMenuItem(
                  text = { Text("تعديل بيانات الصنف") },
                  leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                  onClick = {
                    showMenu = false
                    onEdit()
                  }
                )
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text("حذف الصنف", color = MaterialTheme.colorScheme.error) },
                  leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                  onClick = {
                    showMenu = false
                    onDelete()
                  }
                )
              }
            }
          }
        }
      }

      // Middle: Current Stock Level Bar & Numbers
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
          .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = if (item.currentStock % 1.0 == 0.0) "${item.currentStock.toInt()}" else "%.1f".format(item.currentStock),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = if (item.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = item.unit,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Text(
            text = "الحد الأدنى للتنبيه: ${item.minThreshold.toInt()} ${item.unit}",
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isLowStock) MaterialTheme.colorScheme.error else Color.Gray,
            fontWeight = if (item.isLowStock) FontWeight.Bold else FontWeight.Normal
          )
        }

        // Progress bar
        LinearProgressIndicator(
          progress = { item.stockHealthPercent },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = when {
            item.isOutOfStock -> MaterialTheme.colorScheme.error
            item.isLowStock -> Color(0xFFF59E0B) // Amber
            else -> Color(0xFF10B981) // Emerald Green
          },
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }

      // Bottom Row: Pricing / Supplier info + Quick Action Buttons (+ / - / Reorder)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Supplier and Unit Cost
        Column {
          if (item.supplierName.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
              Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.Gray)
              Text(
                text = item.supplierName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          if (userRole != UserRole.STAFF && item.unitCost > 0) {
            Text(
              text = "سعر الوحدة: ${item.unitCost} $currency",
              style = MaterialTheme.typography.labelSmall,
              color = Color.Gray
            )
          }
        }

        // Quick adjust + / - and Reorder buttons
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Reorder button for low stock
          if (item.isLowStock) {
            FilledTonalButton(
              onClick = onReorder,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              modifier = Modifier.testTag("reorder_btn_${item.id}"),
              colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
              Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
              Spacer(Modifier.width(4.dp))
              Text("طلب", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
            }
          }

          // Consume (-1) Button
          OutlinedIconButton(
            onClick = onQuickConsume,
            modifier = Modifier
              .size(36.dp)
              .testTag("quick_consume_btn_${item.id}"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.Remove, contentDescription = "صرف 1", modifier = Modifier.size(18.dp))
          }

          // Add (+1) Button
          IconButton(
            onClick = onQuickAdd,
            modifier = Modifier
              .size(36.dp)
              .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
              .testTag("quick_add_btn_${item.id}")
          ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة 1", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
fun TransactionCardItem(tx: InventoryTransaction) {
  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = CircleShape,
          color = if (tx.type.isAddition) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
          modifier = Modifier.size(32.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (tx.type.isAddition) Icons.Default.Add else Icons.Default.Remove,
              contentDescription = null,
              tint = if (tx.type.isAddition) Color(0xFF166534) else Color(0xFF991B1B),
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Column {
          Text(text = tx.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          Text(
            text = "${tx.type.titleAr} • ${tx.performedByName} (${DateUtils.formatShortDate(tx.date)})",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
          )
          if (tx.reasonOrReference.isNotEmpty()) {
            Text(
              text = "البيان: ${tx.reasonOrReference}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = if (tx.quantityChange > 0) "+${tx.quantityChange}" else "${tx.quantityChange}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = if (tx.type.isAddition) Color(0xFF166534) else Color(0xFF991B1B)
        )
        Text(
          text = "الرصيد: ${tx.newStockLevel}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.Gray
        )
      }
    }
  }
}

@Composable
fun AddEditInventoryItemDialog(
  initialItem: InventoryItem?,
  categories: List<String>,
  onDismiss: () -> Unit,
  onSave: (InventoryItem) -> Unit
) {
  var name by remember { mutableStateOf(initialItem?.name ?: "") }
  var selectedCategory by remember { mutableStateOf(initialItem?.category ?: categories.firstOrNull() ?: "مواد الطبعات") }
  var currentStockStr by remember { mutableStateOf(initialItem?.currentStock?.toInt()?.toString() ?: "5") }
  var minThresholdStr by remember { mutableStateOf(initialItem?.minThreshold?.toInt()?.toString() ?: "3") }
  var unit by remember { mutableStateOf(initialItem?.unit ?: "علبة") }
  var unitCostStr by remember { mutableStateOf(initialItem?.unitCost?.toString() ?: "0.0") }
  var supplierName by remember { mutableStateOf(initialItem?.supplierName ?: "") }
  var supplierPhone by remember { mutableStateOf(initialItem?.supplierPhone ?: "") }
  var location by remember { mutableStateOf(initialItem?.location ?: "مخزن العيادة") }
  var notes by remember { mutableStateOf(initialItem?.notes ?: "") }

  var nameError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initialItem == null) "إضافة مادة سنية جديدة" else "تعديل بيانات مادة المخزون",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          OutlinedTextField(
            value = name,
            onValueChange = {
              name = it
              nameError = false
            },
            label = { Text("اسم المادة / الصنف *") },
            placeholder = { Text("مثال: ألجينات سريعة التصلب") },
            isError = nameError,
            supportingText = if (nameError) { { Text("يرجى إدخال اسم المادة") } } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_inventory_name")
          )
        }

        // Category selection
        item {
          Text("التصنيف:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            categories.forEach { cat ->
              val isSel = selectedCategory == cat
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable { selectedCategory = cat }
              ) {
                Text(
                  text = cat,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }
        }

        // Stock & Threshold
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = currentStockStr,
              onValueChange = { currentStockStr = it },
              label = { Text("الرصيد الحالي *") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f).testTag("input_current_stock")
            )
            OutlinedTextField(
              value = minThresholdStr,
              onValueChange = { minThresholdStr = it },
              label = { Text("حد التنبيه (الأدنى) *") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f).testTag("input_min_threshold")
            )
          }
        }

        // Unit & Unit Cost
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = unit,
              onValueChange = { unit = it },
              label = { Text("وحدة القياس") },
              placeholder = { Text("علبة، كيس، قرص..") },
              modifier = Modifier.weight(1f).testTag("input_unit")
            )
            OutlinedTextField(
              value = unitCostStr,
              onValueChange = { unitCostStr = it },
              label = { Text("سعر الوحدة") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f).testTag("input_unit_cost")
            )
          }
        }

        // Supplier info
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = supplierName,
              onValueChange = { supplierName = it },
              label = { Text("اسم المورد / الشركة") },
              modifier = Modifier.weight(1.2f).testTag("input_supplier_name")
            )
            OutlinedTextField(
              value = supplierPhone,
              onValueChange = { supplierPhone = it },
              label = { Text("هاتف المورد") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              modifier = Modifier.weight(1f).testTag("input_supplier_phone")
            )
          }
        }

        // Location & Notes
        item {
          OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("موقع التخزين / الرف") },
            placeholder = { Text("مثال: دولاب الطبعات A1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_location")
          )
        }

        item {
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات إضافية") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("input_notes")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            nameError = true
            return@Button
          }
          val stock = currentStockStr.toDoubleOrNull() ?: 0.0
          val threshold = minThresholdStr.toDoubleOrNull() ?: 3.0
          val cost = unitCostStr.toDoubleOrNull() ?: 0.0

          val newItem = (initialItem ?: InventoryItem(
            name = name.trim(),
            category = selectedCategory,
            currentStock = stock,
            minThreshold = threshold,
            unit = unit.trim().ifEmpty { "علبة" },
            unitCost = cost,
            supplierName = supplierName.trim(),
            supplierPhone = supplierPhone.trim(),
            location = location.trim().ifEmpty { "مخزن العيادة" },
            notes = notes.trim()
          )).copy(
            name = name.trim(),
            category = selectedCategory,
            currentStock = stock,
            minThreshold = threshold,
            unit = unit.trim().ifEmpty { "علبة" },
            unitCost = cost,
            supplierName = supplierName.trim(),
            supplierPhone = supplierPhone.trim(),
            location = location.trim().ifEmpty { "مخزن العيادة" },
            notes = notes.trim()
          )

          onSave(newItem)
        },
        modifier = Modifier.testTag("save_inventory_item_btn")
      ) {
        Text("حفظ")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}

@Composable
fun AdjustStockDialog(
  item: InventoryItem,
  onDismiss: () -> Unit,
  onConfirm: (amount: Double, type: InventoryTransactionType, reason: String) -> Unit
) {
  var amountStr by remember { mutableStateOf("1") }
  var selectedType by remember { mutableStateOf(InventoryTransactionType.STOCK_IN) }
  var reason by remember { mutableStateOf("") }

  val transactionTypes = listOf(
    InventoryTransactionType.STOCK_IN,
    InventoryTransactionType.USAGE_OUT,
    InventoryTransactionType.ADJUSTMENT_ADD,
    InventoryTransactionType.ADJUSTMENT_SUBTRACT
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تعديل رصيد مادة: ${item.name}", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "الرصيد الحالي: ${item.currentStock.toInt()} ${item.unit}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        Text("نوع الحركة:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          transactionTypes.forEach { type ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedType = type },
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = selectedType == type,
                onClick = { selectedType = type }
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = type.titleAr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = { Text("الكمية (${item.unit}) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_adjust_amount")
        )

        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("البيان / سبب التعديل") },
          placeholder = { Text("مثال: شراء دفعة، استخدام لإرسالية...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_adjust_reason")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 1.0
          onConfirm(amt, selectedType, reason.trim())
        },
        modifier = Modifier.testTag("confirm_adjust_stock_btn")
      ) {
        Text("تأكيد وحفظ")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}

@Composable
fun ReorderItemDialog(
  item: InventoryItem,
  onDismiss: () -> Unit,
  onSendOrder: (orderQty: Double) -> Unit,
  onCallSupplier: () -> Unit
) {
  var orderQtyStr by remember { mutableStateOf(item.reorderQuantity.toInt().toString().ifEmpty { "5" }) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
    },
    title = { Text("طلب توريد مادة سنية", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("الرصيد الحالي: ${item.currentStock.toInt()} ${item.unit} (نقص مخزون!)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text("المورد: ${item.supplierName.ifEmpty { "غير محدد" }}", style = MaterialTheme.typography.bodySmall)
            Text("الهاتف: ${item.supplierPhone.ifEmpty { "غير محدد" }}", style = MaterialTheme.typography.bodySmall)
          }
        }

        OutlinedTextField(
          value = orderQtyStr,
          onValueChange = { orderQtyStr = it },
          label = { Text("الكمية المطلوبة (${item.unit})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_reorder_qty")
        )

        if (item.supplierPhone.isNotEmpty()) {
          OutlinedButton(
            onClick = onCallSupplier,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("اتصال هاتفي بالمورد (${item.supplierPhone})")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val qty = orderQtyStr.toDoubleOrNull() ?: 5.0
          onSendOrder(qty)
        },
        modifier = Modifier.testTag("send_whatsapp_order_btn")
      ) {
        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("إرسال الطلب عبر واتساب")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}
