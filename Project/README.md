# 🛡️ Stickman Tower Defense (Android Native)

โปรเจกต์เกมแนว Tower Defense ที่พัฒนาด้วย **Kotlin** และ **Jetpack Compose** โดยเน้นการใช้กราฟิกแบบ Custom Canvas และการจัดการสถานะเกมแบบ Real-time (FPS Optimized)

## 🌟 ฟีเจอร์เด่น (Key Features)
- **Custom Canvas Engine:** วาดตัวละคร Stickman และเอฟเฟกต์ทั้งหมดด้วยโค้ด (Android DrawScope) ไม่มีการใช้ไฟล์ภาพภายนอก ทำให้เกมมีขนาดเล็กและลื่นไหล
- **Advanced Tower Logic:**
  - `Wizard`: ระบบ Chain Lightning (สายฟ้าชิ่ง) โจมตีศัตรูต่อเนื่องตามจำนวนเลเวล
  - `Pyro`: สร้างสถานะ Burn (เผาไหม้) และ Slow (เดินช้า) แบบ Stackable
  - `Station`: ระบบยูนิตพิเศษ ปล่อยรถขยะ (Garbage Truck) วิ่งสวนทางเพื่อชนศัตรู (Kamikaze Logic)
- **Game Mechanics:**
  - ระบบ **Upgrade System**: อัปเกรดหอคอยได้ 3 ระดับ พร้อมการคำนวณสเตตัสแบบ Dynamic (DMG, Range, Fire Rate)
  - ระบบ **Game Speed**: ปรับความเร็วเกม X1 และ X2 โดยใช้เทคนิค Repeat Logic ใน Game Loop
  - ระบบ **Boss Wave**: บอสพร้อมแถบเลือดพิเศษ (Boss HP Bar) ปรากฏตัวตาม Wave ที่กำหนด

## 🛠️ Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Declarative UI)
- **Graphics:** Android Canvas API (DrawScope)
- **Concurrency:** Kotlin Coroutines (จัดการ Game Tick Loop ที่ 60 FPS)

## 🎮 วิธีการเล่น
1. เลือกประเภทหอคอยจากเมนูด้านล่าง
2. แตะพื้นที่ว่างบนแผนที่เพื่อสร้างหอคอย
3. คลิกที่หอคอยเพื่อเปิดเมนูอัปเกรด
4. ป้องกันไม่ให้ศัตรูเข้าถึงบ้าน (🏠) จนเลือดหมด
