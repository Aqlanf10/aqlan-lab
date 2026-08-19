package com.example.data

import com.example.data.models.*

object DatabaseSeedData {
  val defaultUsers = listOf(
    User(id = 1, username = "aqlan", fullName = "د. عقلان الكامل", role = UserRole.ADMIN, pinCode = "1111", avatarColor = 0xFFD32F2F),
    User(id = 2, username = "staff1", fullName = "مروة العريقي", role = UserRole.STAFF, pinCode = "2222", avatarColor = 0xFF1976D2),
    User(id = 3, username = "accountant1", fullName = "عمر باحميد", role = UserRole.ACCOUNTANT, pinCode = "3333", avatarColor = 0xFF388E3C)
  )

  val defaultLabs = listOf(
    Laboratory(
      id = 1,
      name = "مختبر الأمل لطب الأسنان",
      phone = "+967 771 234 567",
      address = "شارع حدة - صنعاء",
      managerName = "فني/ خالد الرازحي",
      offeredWorkTypes = "Zirconia, E-max, PFM, Implant Crown",
      defaultCurrency = "SAR",
      status = LabStatus.ACTIVE,
      notes = "معمل ممتاز في التيجان والجسور السيراميكية، التعامل بالريال السعودي (SAR)"
    ),
    Laboratory(
      id = 2,
      name = "مختبر الإبداع الرقمي Cad/Cam",
      phone = "+967 733 987 654",
      address = "حي الأصبحي - صنعاء",
      managerName = "م. سامي النهاري",
      offeredWorkTypes = "Zirconia 3D, Veneer, Inlay/Onlay, Bridge",
      defaultCurrency = "SAR",
      status = LabStatus.ACTIVE,
      notes = "تجهيزات Cad/Cam حديثة وخبرة عالية في الفينير والابتسامات التجميلية (SAR)"
    ),
    Laboratory(
      id = 3,
      name = "مختبر النخبة لتعويضات الأسنان",
      phone = "+967 711 456 789",
      address = "شارع الزبيري - صنعاء",
      managerName = "أ. ياسر القاضي",
      offeredWorkTypes = "Denture, Acrylic, Retainer, Night Guard",
      defaultCurrency = "YER",
      status = LabStatus.ACTIVE,
      notes = "متخصص في أطقم الأسنان والأجهزة التقويمية، التعامل بالريال اليمني (YER)"
    ),
    Laboratory(
      id = 4,
      name = "مختبر المستقبل لزراعة وتجميل الأسنان",
      phone = "+967 775 889 900",
      address = "شارع الستين الجنوبي",
      managerName = "د. جمال الشامي",
      offeredWorkTypes = "Implant Crown, Customized Abutment, Zirconia Multilayer",
      defaultCurrency = "USD",
      status = LabStatus.ACTIVE,
      notes = "مختبر رائد في حلول زراعة الأسنان، التعامل بالدولار الأمريكي (USD)"
    )
  )

  val defaultWorkTypes = listOf(
    WorkType(id = 1, nameAr = "تاج زركونيا (Zirconia Crown)", nameEn = "Zirconia Crown", description = "تاج زركون عالي الشفافية والقوة", defaultPrice = 35.0, category = "ثابت (Fixed)"),
    WorkType(id = 2, nameAr = "إيماكس (E-max)", nameEn = "E-max Crown", description = "خزف ليثيوم ديسيليكات عالي الجمالية للأسنان الأمامية", defaultPrice = 45.0, category = "ثابت (Fixed)"),
    WorkType(id = 3, nameAr = "خزف مدمج بمعدن (PFM)", nameEn = "PFM Crown", description = "تاج بورسلين مدعم بهيكل معدني متين", defaultPrice = 25.0, category = "ثابت (Fixed)"),
    WorkType(id = 4, nameAr = "فينير تجميلي (Veneer)", nameEn = "Laminate Veneer", description = "قشور خزفية رقيقة لتجميل الابتسامة", defaultPrice = 50.0, category = "تجميلي (Cosmetic)"),
    WorkType(id = 5, nameAr = "جسر زركونيا (Bridge)", nameEn = "Zirconia Bridge", description = "جسر زركونيا متعدد الوحدات لتعويض الأسنان المفقودة", defaultPrice = 35.0, category = "ثابت (Fixed)"),
    WorkType(id = 6, nameAr = "طقم أسنان كامل (Full Denture)", nameEn = "Full Denture", description = "طقم متحرك كامل أكريليك بأسنان عالية الجودة", defaultPrice = 80.0, category = "متحرك (Removable)"),
    WorkType(id = 7, nameAr = "طقم أسنان جزئي (Partial Denture)", nameEn = "Partial Denture", description = "طقم متحرك جزئي أكريليك أو كاست بارتيل", defaultPrice = 60.0, category = "متحرك (Removable)"),
    WorkType(id = 8, nameAr = "واقي ليلي (Night Guard)", nameEn = "Night Guard", description = "حارس ليلي صلب أو مرن لعلاج صرير الأسنان", defaultPrice = 20.0, category = "وقائي (Preventive)"),
    WorkType(id = 9, nameAr = "مثبت تقويمي (Retainer)", nameEn = "Hawley/Clear Retainer", description = "مثبت شفاف أو هولي لتثبيت الأسنان بعد التقويم", defaultPrice = 18.0, category = "تقويم (Orthodontics)"),
    WorkType(id = 10, nameAr = "تاج على زرعة (Implant Crown)", nameEn = "Implant Crown", description = "تاج زركونيا أو بي إف إم مثبت ببرغي أو لاصق على دعامة الغرسة", defaultPrice = 65.0, category = "زراعة (Implant)")
  )

  // Smart Lab Custom Pricing overrides
  val defaultLabPrices = listOf(
    LabPrice(id = 1, labId = 1, workTypeId = 1, customPrice = 30.0), // مختبر الأمل -> Zirconia: $30 (خصم خاص)
    LabPrice(id = 2, labId = 1, workTypeId = 2, customPrice = 40.0),
    LabPrice(id = 3, labId = 2, workTypeId = 1, customPrice = 32.0), // مختبر الإبداع -> Zirconia: $32
    LabPrice(id = 4, labId = 2, workTypeId = 4, customPrice = 45.0), // مختبر الإبداع -> Veneer: $45
    LabPrice(id = 5, labId = 3, workTypeId = 6, customPrice = 70.0), // مختبر النخبة -> Full Denture: $70
    LabPrice(id = 6, labId = 3, workTypeId = 8, customPrice = 15.0),
    LabPrice(id = 7, labId = 4, workTypeId = 10, customPrice = 60.0) // مختبر المستقبل -> Implant: $60
  )

  val defaultShipments = listOf(
    Shipment(
      id = 1,
      shipmentNumber = "#000125",
      orderDate = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L),
      clinicOrDoctorName = "د. طارق الحكيمي",
      patientName = "محمد عبدالسلام",
      labId = 1,
      labName = "مختبر الأمل لطب الأسنان",
      workTypeId = 1,
      workTypeName = "تاج زركونيا (Zirconia Crown)",
      pieceCount = 3,
      toothNumbers = "11, 12, 21",
      shade = "A2",
      shadeNotes = "تدرج شفافية في الحافة القاطعة (Incisal Translucency)",
      expectedDeliveryDate = System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L),
      status = ShipmentStatus.IN_PROGRESS,
      notes = "تأكيد ملاءمة الحواف الإطباقية مع السن 22 المقابل",
      currency = "SAR",
      unitPrice = 30.0,
      totalPrice = 90.0,
      discount = 0.0,
      isUrgent = false,
      createdByUserId = 2,
      createdByName = "مروة العريقي"
    ),
    Shipment(
      id = 2,
      shipmentNumber = "#000126",
      orderDate = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L),
      clinicOrDoctorName = "د. ريم المقطري",
      patientName = "سارة أحمد الشامي",
      labId = 2,
      labName = "مختبر الإبداع الرقمي Cad/Cam",
      workTypeId = 4,
      workTypeName = "فينير تجميلي (Veneer)",
      pieceCount = 6,
      toothNumbers = "13, 12, 11, 21, 22, 23",
      shade = "BL2",
      shadeNotes = "Bleach Shade BL2 مع بريق طبيعي",
      expectedDeliveryDate = System.currentTimeMillis() + (1 * 24 * 60 * 60 * 1000L),
      status = ShipmentStatus.READY,
      notes = "العمل جاهز في المختبر وجاري إرسال المندوب لاستلامه",
      currency = "SAR",
      unitPrice = 45.0,
      totalPrice = 270.0,
      discount = 20.0,
      isUrgent = true,
      createdByUserId = 1,
      createdByName = "د. أحمد الخالد"
    ),
    Shipment(
      id = 3,
      shipmentNumber = "#000127",
      orderDate = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L),
      clinicOrDoctorName = "د. حسام الوزير",
      patientName = "فؤاد هزاع",
      labId = 3,
      labName = "مختبر النخبة لتعويضات الأسنان",
      workTypeId = 6,
      workTypeName = "طقم أسنان كامل (Full Denture)",
      pieceCount = 2,
      toothNumbers = "Upper Arch, Lower Arch",
      shade = "A3",
      shadeNotes = "لون لثة طبيعي Pink Gingiva #3",
      expectedDeliveryDate = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      actualReceivedDate = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      status = ShipmentStatus.RECEIVED,
      notes = "تم الاستلام بنجاح ومطابق للقياسات الشمعية",
      currency = "YER",
      unitPrice = 35000.0,
      totalPrice = 70000.0,
      discount = 0.0,
      isUrgent = false,
      createdByUserId = 2,
      createdByName = "مروة العريقي"
    ),
    Shipment(
      id = 4,
      shipmentNumber = "#000128",
      orderDate = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      clinicOrDoctorName = "د. نادية الصبري",
      patientName = "ياسمين منصر",
      labId = 1,
      labName = "مختبر الأمل لطب الأسنان",
      workTypeId = 2,
      workTypeName = "إيماكس (E-max)",
      pieceCount = 2,
      toothNumbers = "11, 21",
      shade = "A1",
      shadeNotes = "High Translucency (HT)",
      expectedDeliveryDate = System.currentTimeMillis() + (4 * 24 * 60 * 60 * 1000L),
      status = ShipmentStatus.NEW,
      notes = "يرجى الاهتمام بالشكل التشريحي وتطابق خط الابتسامة",
      currency = "SAR",
      unitPrice = 40.0,
      totalPrice = 80.0,
      discount = 0.0,
      isUrgent = false,
      createdByUserId = 2,
      createdByName = "مروة العريقي"
    ),
    Shipment(
      id = 5,
      shipmentNumber = "#000129",
      orderDate = System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L),
      clinicOrDoctorName = "د. طارق الحكيمي",
      patientName = "عبدالرحمن ثابت",
      labId = 4,
      labName = "مختبر المستقبل لزراعة وتجميل الأسنان",
      workTypeId = 10,
      workTypeName = "تاج على زرعة (Implant Crown)",
      pieceCount = 1,
      toothNumbers = "36",
      shade = "A3.5",
      shadeNotes = "Screw-retained with hex connection",
      expectedDeliveryDate = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L), // Late/Delayed
      status = ShipmentStatus.IN_PROGRESS,
      notes = "تأخر المعمل في توريد الدعامة المخصصة، تم التواصل مع الفني للمتابعة العاجلة",
      currency = "USD",
      unitPrice = 60.0,
      totalPrice = 60.0,
      discount = 0.0,
      isUrgent = true,
      createdByUserId = 1,
      createdByName = "د. أحمد الخالد"
    )
  )

  val defaultPayments = listOf(
    Payment(
      id = 1,
      labId = 1,
      labName = "مختبر الأمل لطب الأسنان",
      amount = 300.0,
      currency = "SAR",
      paidAmount = 300.0,
      paidCurrency = "SAR",
      exchangeRate = 1.0,
      paymentDate = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L),
      paymentMethod = PaymentMethod.CASH,
      receiptNumber = "REC-8841",
      notes = "دفعة حساب أعمال الأسبوع الأول من الشهر (SAR)",
      recordedByUserId = 3,
      recordedByName = "عمر باحميد"
    ),
    Payment(
      id = 2,
      labId = 2,
      labName = "مختبر الإبداع الرقمي Cad/Cam",
      amount = 250.0,
      currency = "SAR",
      paidAmount = 250.0,
      paidCurrency = "SAR",
      exchangeRate = 1.0,
      paymentDate = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L),
      paymentMethod = PaymentMethod.BANK_TRANSFER,
      receiptNumber = "TRF-90214",
      notes = "تحويل بنكي عبر تطبيق بنك الكريمي (SAR)",
      recordedByUserId = 3,
      recordedByName = "عمر باحميد"
    ),
    Payment(
      id = 3,
      labId = 3,
      labName = "مختبر النخبة لتعويضات الأسنان",
      amount = 50000.0,
      currency = "YER",
      paidAmount = 50000.0,
      paidCurrency = "YER",
      exchangeRate = 1.0,
      paymentDate = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L),
      paymentMethod = PaymentMethod.CASH,
      receiptNumber = "REC-3301",
      notes = "دفعة نقدية بالريال اليمني",
      recordedByUserId = 3,
      recordedByName = "عمر باحميد"
    ),
    Payment(
      id = 4,
      labId = 4,
      labName = "مختبر المستقبل لزراعة وتجميل الأسنان",
      amount = 60.0,
      currency = "USD",
      paidAmount = 60.0,
      paidCurrency = "USD",
      exchangeRate = 1.0,
      paymentDate = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      paymentMethod = PaymentMethod.CASH,
      receiptNumber = "REC-4412",
      notes = "سداد قيمة تاج الزرعة بالدولار",
      recordedByUserId = 3,
      recordedByName = "عمر باحميد"
    )
  )

  val defaultAuditLogs = listOf(
    AuditLog(
      id = 1,
      timestamp = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L),
      userId = 3,
      userName = "عمر باحميد",
      userRole = UserRole.ACCOUNTANT,
      actionType = AuditActionType.RECORD_PAYMENT,
      description = "تسجيل دفعة 300$ لمختبر الأمل (إيصال REC-8841)",
      entityId = 1,
      entityType = "Payment"
    ),
    AuditLog(
      id = 2,
      timestamp = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L),
      userId = 2,
      userName = "مروة العريقي",
      userRole = UserRole.STAFF,
      actionType = AuditActionType.CREATE_SHIPMENT,
      description = "إنشاء إرسالية #000127 (طقم كامل - مختبر النخبة)",
      entityId = 3,
      entityType = "Shipment"
    ),
    AuditLog(
      id = 3,
      timestamp = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L),
      userId = 1,
      userName = "د. أحمد الخالد",
      userRole = UserRole.ADMIN,
      actionType = AuditActionType.CREATE_SHIPMENT,
      description = "إنشاء إرسالية #000126 (6 فينير BL2 - مختبر الإبداع)",
      entityId = 2,
      entityType = "Shipment"
    ),
    AuditLog(
      id = 4,
      timestamp = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L),
      userId = 2,
      userName = "مروة العريقي",
      userRole = UserRole.STAFF,
      actionType = AuditActionType.CREATE_SHIPMENT,
      description = "إنشاء إرسالية #000125 (3 زركونيا A2 - مختبر الأمل)",
      entityId = 1,
      entityType = "Shipment"
    ),
    AuditLog(
      id = 5,
      timestamp = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      userId = 2,
      userName = "مروة العريقي",
      userRole = UserRole.STAFF,
      actionType = AuditActionType.UPDATE_STATUS,
      description = "تحديث حالة الإرسالية #000127 إلى 'تم الاستلام'",
      entityId = 3,
      entityType = "Shipment"
    ),
    AuditLog(
      id = 6,
      timestamp = System.currentTimeMillis() - (4 * 60 * 60 * 1000L),
      userId = 1,
      userName = "د. أحمد الخالد",
      userRole = UserRole.ADMIN,
      actionType = AuditActionType.UPDATE_STATUS,
      description = "تحديث حالة الإرسالية #000126 إلى 'جاهزة'",
      entityId = 2,
      entityType = "Shipment"
    )
  )

  val defaultSettings = listOf(
    AppSetting("currency", "USD"), // USD, SAR, YER
    AppSetting("clinic_name", "مركز النخبة التخصصي لطب وجراحة الأسنان"),
    AppSetting("clinic_phone", "+967 1 445566"),
    AppSetting("active_user_id", "1") // Defaults to Admin
  )

  val defaultInventoryItems = listOf(
    InventoryItem(
      id = 1,
      name = "ألجينات طب أسنان سريعة التصلب (Alginate Class A)",
      category = "مواد الطبعات",
      currentStock = 2.0, // Low stock: <= 5
      minThreshold = 5.0,
      reorderQuantity = 10.0,
      unit = "كيس 500g",
      unitCost = 6.5,
      supplierName = "شركة الرواد للوازم الطبية",
      supplierPhone = "+967 771 000 111",
      location = "دولاب مواد الطبعات A1",
      lastRestockedDate = System.currentTimeMillis() - (15 * 24 * 60 * 60 * 1000L),
      notes = "ألجينات عالية الدقة خالية من الغبار متغيرة الألوان"
    ),
    InventoryItem(
      id = 2,
      name = "أقراص زركونيا 98mm متعددة الطبقات 3D Multilayer",
      category = "الخزف والزركونيا",
      currentStock = 2.0, // Low stock: <= 4
      minThreshold = 4.0,
      reorderQuantity = 6.0,
      unit = "قرص (Disc)",
      unitCost = 75.0,
      supplierName = "مؤسسة كاد كام لتقنيات الأسنان",
      supplierPhone = "+967 733 222 333",
      location = "مخزن كاد/كام - رف 3",
      lastRestockedDate = System.currentTimeMillis() - (20 * 24 * 60 * 60 * 1000L),
      notes = "شفافية فائقة وتدرج ألوان ممتاز للتيجان الأمامية"
    ),
    InventoryItem(
      id = 3,
      name = "جبس أصفر صلب للدايات والصب (Type IV Die Stone 5kg)",
      category = "الجبس والشمع",
      currentStock = 1.0, // Low stock: <= 3
      minThreshold = 3.0,
      reorderQuantity = 5.0,
      unit = "كيس 5kg",
      unitCost = 14.0,
      supplierName = "مكتب النخبة للوازم المعامل",
      supplierPhone = "+967 775 444 555",
      location = "مستودع الجبس الأرضي",
      lastRestockedDate = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L),
      notes = "تمدد خطي منخفض جداً وصلابة ممتازة لحدود التحضير"
    ),
    InventoryItem(
      id = 4,
      name = "مكعبات سيراميك إيماكس كاد (E-max CAD Blocks C14)",
      category = "الخزف والزركونيا",
      currentStock = 8.0, // In stock
      minThreshold = 4.0,
      reorderQuantity = 8.0,
      unit = "علبة (5 قطع)",
      unitCost = 90.0,
      supplierName = "مؤسسة كاد كام لتقنيات الأسنان",
      supplierPhone = "+967 733 222 333",
      location = "مخزن كاد/كام - خزانة آمنة",
      lastRestockedDate = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L),
      notes = "مكعبات HT و LT لأجهزة الخراطة المباشرة"
    ),
    InventoryItem(
      id = 5,
      name = "سيليكون إضافة للطبعات ثقيل وخفيف (A-Silicone Kit)",
      category = "مواد الطبعات",
      currentStock = 6.0, // In stock
      minThreshold = 3.0,
      reorderQuantity = 6.0,
      unit = "طقم متكامل",
      unitCost = 35.0,
      supplierName = "شركة الرواد للوازم الطبية",
      supplierPhone = "+967 771 000 111",
      location = "دولاب مواد الطبعات A2",
      lastRestockedDate = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L),
      notes = "Heavy Body مع خراطيش Light Body ومسدس حقن"
    ),
    InventoryItem(
      id = 6,
      name = "إسمنت لاصق زجاجي دائم (Glass Ionomer Luting Cement)",
      category = "المواد اللاصقة والاستهلاكيات",
      currentStock = 1.0, // Low stock: <= 3
      minThreshold = 3.0,
      reorderQuantity = 4.0,
      unit = "عبوة بودرة وسائل",
      unitCost = 28.0,
      supplierName = "مؤسسة الشفاء لتجهيزات الأسنان",
      supplierPhone = "+967 711 888 999",
      location = "صيدلية وخزائن العيادة 1",
      lastRestockedDate = System.currentTimeMillis() - (25 * 24 * 60 * 60 * 1000L),
      notes = "إسمنت تثبيت للتيجان المعدنية والخزفية وتفريغ فلورايد"
    ),
    InventoryItem(
      id = 7,
      name = "شمع تشكيل ونمذجة أزرق للتيجان (Inlay/Crown Wax Blue)",
      category = "الجبس والشمع",
      currentStock = 5.0, // In stock
      minThreshold = 2.0,
      reorderQuantity = 4.0,
      unit = "علبة",
      unitCost = 12.0,
      supplierName = "مكتب النخبة للوازم المعامل",
      supplierPhone = "+967 775 444 555",
      location = "طاولة تشمع المعمل B",
      lastRestockedDate = System.currentTimeMillis() - (12 * 24 * 60 * 60 * 1000L),
      notes = "شمع صلب عديم الرماد للاحتراق الكامل"
    ),
    InventoryItem(
      id = 8,
      name = "أكريليك وردي حار لتعويضات الأسنان (Heat Cure Acrylic Resin)",
      category = "الأكريليك والتعويضات",
      currentStock = 2.0, // Low stock: <= 4
      minThreshold = 4.0,
      reorderQuantity = 5.0,
      unit = "عبوة 1kg",
      unitCost = 22.0,
      supplierName = "مكتب النخبة للوازم المعامل",
      supplierPhone = "+967 775 444 555",
      location = "مخزن الأكريليك والبوليمر",
      lastRestockedDate = System.currentTimeMillis() - (18 * 24 * 60 * 60 * 1000L),
      notes = "أكريليك ذو ثبات لوني عالي ومقاوم للكسر للأطقم الكاملة"
    ),
    InventoryItem(
      id = 9,
      name = "بنج تخدير موضعي أرتيكائين 4% (Articaine 1:100000 50pcs)",
      category = "المواد اللاصقة والاستهلاكيات",
      currentStock = 7.0, // In stock
      minThreshold = 3.0,
      reorderQuantity = 10.0,
      unit = "علبة (50 كاربول)",
      unitCost = 32.0,
      supplierName = "مؤسسة الشفاء لتجهيزات الأسنان",
      supplierPhone = "+967 711 888 999",
      location = "ثلاجة المواد الدوائية",
      lastRestockedDate = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L),
      notes = "تخدير عميق وسريع لجراحات الأسنان والتحضير الحيوي"
    ),
    InventoryItem(
      id = 10,
      name = "فراشي وأقراص تلميع وتنعيم الزركونيا والبورسلين",
      category = "الخزف والزركونيا",
      currentStock = 4.0, // In stock
      minThreshold = 2.0,
      reorderQuantity = 4.0,
      unit = "طقم رؤوس",
      unitCost = 18.0,
      supplierName = "مؤسسة كاد كام لتقنيات الأسنان",
      supplierPhone = "+967 733 222 333",
      location = "درج الفني - قسم الفنشينج",
      lastRestockedDate = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L),
      notes = "حبيبات ألماسية مدمجة للتلميع الجاف والرطب دون حرارة"
    )
  )

  val defaultInventoryTransactions = listOf(
    InventoryTransaction(
      id = 1,
      itemId = 1,
      itemName = "ألجينات طب أسنان سريعة التصلب (Alginate Class A)",
      type = InventoryTransactionType.USAGE_OUT,
      quantityChange = -2.0,
      newStockLevel = 2.0,
      date = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L),
      performedByName = "مروة العريقي",
      reasonOrReference = "استهلاك لطبعات مرضى العيادة"
    ),
    InventoryTransaction(
      id = 2,
      itemId = 2,
      itemName = "أقراص زركونيا 98mm متعددة الطبقات 3D Multilayer",
      type = InventoryTransactionType.USAGE_OUT,
      quantityChange = -1.0,
      newStockLevel = 2.0,
      date = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L),
      performedByName = "د. أحمد الخالد",
      reasonOrReference = "خراطة جسر زركونيا 6 وحدات #000126"
    ),
    InventoryTransaction(
      id = 3,
      itemId = 4,
      itemName = "مكعبات سيراميك إيماكس كاد (E-max CAD Blocks C14)",
      type = InventoryTransactionType.STOCK_IN,
      quantityChange = 4.0,
      newStockLevel = 8.0,
      date = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L),
      performedByName = "عمر باحميد",
      reasonOrReference = "توريد شحنة جديدة من شركة كاد كام"
    )
  )
}
