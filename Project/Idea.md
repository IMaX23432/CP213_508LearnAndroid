🎮 Tower Defense Game (MVP)
🎯 เป้าหมาย MVP
เล่นได้จริง 1 ด่าน
วางป้อม → ศัตรูเดิน → ป้อมยิง → ชนะ/แพ้
ไม่มีระบบซับซ้อน (ไม่ผิด ToS เกมใด ๆ เพราะเป็นเกมใหม่ของเราเอง)
🧱 Core Gameplay (ขั้นต่ำสุด)
1. แผนที่ (Map)
เป็น Grid 8x12 หรือ 10x10
เส้นทางศัตรู fix ไว้ (hardcode)
ช่องว่างสำหรับวางป้อม
S → → ↓
      ↓
→ → → E
S = Spawn
E = End
2. ศัตรู (Enemy)

ขั้นต่ำ:

HP
Speed
Position (x, y)
เดินตาม path ทีละ node

MVP มีแค่ ศัตรูชนิดเดียว

3. ป้อม (Tower)

ขั้นต่ำ:

Damage
Range
Fire rate

MVP:

มีป้อมแค่ 1 แบบ
ยิงอัตโนมัติเมื่อศัตรูเข้า range
4. ระบบเงิน
เริ่มเงิน 100
ป้อมราคา 50
ฆ่าศัตรูได้ +10
5. เงื่อนไขจบเกม
ชนะ: ศัตรูหมด
แพ้: ศัตรูถึง End 3 ตัว
🧠 Game Loop (สำคัญมาก)

ใช้ LaunchedEffect + delay() แทน game engine

while (gameRunning) {
    moveEnemies()
    towersAttack()
    checkGameState()
    delay(16L) // ~60 FPS
}

👉 Compose ทำได้ ไม่ต้อง LibGDX

📱 โครงสร้าง Project (แนะนำ)
ui/
 ├─ GameScreen.kt
 ├─ TowerView.kt
 ├─ EnemyView.kt

game/
 ├─ GameState.kt
 ├─ Enemy.kt
 ├─ Tower.kt
 ├─ Path.kt
🎨 UI ด้วย Jetpack Compose
Game Screen
Canvas วาด map + enemy + tower
Tap:
แตะช่อง → วางป้อม
Canvas(modifier = Modifier.fillMaxSize()) {
    drawRect(...)
    drawCircle(...) // enemy
}
🔥 สิ่งที่ “ตัดออก” จาก MVP

❌ อัปเกรดป้อม
❌ หลายด่าน
❌ เอฟเฟกต์
❌ เสียง
❌ ศัตรูหลายแบบ

เอาให้ “เล่นได้ก่อน” สำคัญสุด

🚀 ต่อยอดหลัง MVP
Wave system
Tower upgrade
Enemy หลายแบบ
Save / Load
Speed x2
🧪 ตัวอย่างแรงบันดาลใจ (ไม่ผิด ToS)
Kingdom Rush (แนวคิด)
Infinitode (ระบบ)
Cursed Treasure (classic TD)