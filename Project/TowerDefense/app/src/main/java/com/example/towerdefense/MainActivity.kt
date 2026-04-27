package com.example.towerdefense

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameScreen() }
    }
}

// ---------------- DATA MODELS ----------------

enum class EnemyType(val color: Color, val hp: Float, val speed: Float, val reward: Int, val size: Float) {
    NORMAL(Color(0xFF00BCD4), 280f, 2.5f, 25, 24f),
    FAST(Color(0xFF4CAF50), 200f, 6.0f, 35, 20f),
    TANK(Color(0xFF9C27B0), 3000f, 1.4f, 150, 36f),
    BOSS(Color(0xFFFF1744), 8500f, 1.2f, 1200, 60f)
}

enum class TowerType(val label: String, val cost: Int, val range: Float, val baseDamage: Float, val baseCd: Int, val color: Color) {
    SOLDIER("Soldier", 100, 380f, 85f, 25, Color(0xFF1976D2)),
    SNIPER("Sniper", 500, 100000f, 400f, 120, Color(0xFF388E3C)),
    WIZARD("Wizard", 200, 450f, 90f, 50, Color(0xFF9C27B0)),
    PYRO("Pyro", 300, 320f, 15.0f, 4, Color(0xFFFF5722)),
    STATION("Station", 700, 0f, 0f, 800, Color(0xFF455A64))
}

data class Enemy(
    val id: Long = System.nanoTime(),
    var x: Float, var y: Float,
    var hp: Float, var maxHp: Float,
    var type: EnemyType,
    var pathIndex: Int = 0,
    var walkAnim: Float = 0f,
    var burnTicks: Int = 0,
    var burnDamage: Float = 0f,
    var slowTimer: Int = 0
)

data class Tower(
    val id: Long = System.nanoTime(),
    var x: Float, var y: Float,
    var type: TowerType,
    var level: Int = 1,
    var cd: Int = 0,
    var targetId: Long? = null
)

data class GarbageTruck(
    var x: Float, var y: Float,
    var hp: Float, var maxHp: Float,
    var pathIndex: Int,
    val id: Long = System.nanoTime()
)

data class Projectile(
    var x: Float, var y: Float,
    var targetId: Long,
    val damage: Float,
    val type: TowerType,
    var bounces: Int = 0,
    val hitEnemies: MutableSet<Long> = mutableSetOf(),
    var lightningPath: List<Offset>? = null // สำหรับสายฟ้า Wizard
)

data class DamageText(var x: Float, var y: Float, val text: String, var life: Int = 30)

enum class GameStatus { START, PLAYING, GAME_OVER }

// ---------------- GAME ENGINE ----------------

@Composable
fun GameScreen() {
    var status by remember { mutableStateOf(GameStatus.START) }
    var gameSpeed by remember { mutableIntStateOf(1) } // 1x or 2x

    val enemies = remember { mutableStateListOf<Enemy>() }
    val towers = remember { mutableStateListOf<Tower>() }
    val projectiles = remember { mutableStateListOf<Projectile>() }
    val trucks = remember { mutableStateListOf<GarbageTruck>() }
    val damageTexts = remember { mutableStateListOf<DamageText>() }

    var money by remember { mutableIntStateOf(1000) }
    var baseHP by remember { mutableIntStateOf(20) }
    var wave by remember { mutableIntStateOf(0) }
    var enemiesToSpawn by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(0) }

    var selectedBuild by remember { mutableStateOf<TowerType?>(null) }
    var selectedInGame by remember { mutableStateOf<Tower?>(null) }
    var pathPoints by remember { mutableStateOf(listOf<Offset>()) }

    val textPaint = remember { Paint().apply { color = android.graphics.Color.BLACK; textSize = 24f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }

    LaunchedEffect(status, gameSpeed) {
        if (status != GameStatus.PLAYING) return@LaunchedEffect
        while (status == GameStatus.PLAYING) {
            // Speed X2 Logic: รันลูปซ้ำตามจำนวนความเร็ว
            repeat(gameSpeed) {
                // 1. Wave Logic
                if (enemies.isEmpty() && enemiesToSpawn <= 0) {
                    delay(2000 / gameSpeed.toLong())
                    wave++
                    money += 250 + (wave * 60)
                    enemiesToSpawn = 10 + (wave * 3)
                }

                if (enemiesToSpawn > 0 && pathPoints.isNotEmpty()) {
                    if (spawnCd > 0) spawnCd--
                    else {
                        val type = when {
                            enemiesToSpawn == 1 -> EnemyType.BOSS
                            wave % 4 == 0 && enemiesToSpawn % 5 == 0 -> EnemyType.TANK
                            else -> EnemyType.NORMAL
                        }
                        val hpBoost = 1f + (wave * 0.45f)
                        enemies.add(Enemy(x = pathPoints[0].x, y = pathPoints[0].y, hp = type.hp * hpBoost, maxHp = type.hp * hpBoost, type = type))
                        enemiesToSpawn--; spawnCd = 60
                    }
                }

                // 2. Enemy Movement & Debuffs
                val eIt = enemies.iterator()
                while (eIt.hasNext()) {
                    val e = eIt.next()
                    // Burn Ticks: ทำงานทุก 1 วินาที (60 frames)
                    if (e.burnTicks > 0 && System.currentTimeMillis() % 1000 < 20) {
                        e.hp -= e.burnDamage; e.burnTicks--; damageTexts.add(DamageText(e.x, e.y, e.burnDamage.toInt().toString()))
                    }
                    if (e.slowTimer > 0) e.slowTimer--

                    if (e.hp <= 0) { money += e.type.reward; eIt.remove(); continue }

                    val target = pathPoints.getOrNull(e.pathIndex + 1)
                    if (target != null) {
                        val dx = target.x - e.x; val dy = target.y - e.y; val d = sqrt(dx*dx + dy*dy)
                        val speedMult = if (e.slowTimer > 0) 0.75f else 1.0f
                        if (d < 10f) e.pathIndex++
                        else { e.x += (dx/d)*e.type.speed*speedMult; e.y += (dy/d)*e.type.speed*speedMult }
                        e.walkAnim += 0.35f
                    } else { baseHP--; eIt.remove(); if (baseHP <= 0) status = GameStatus.GAME_OVER }
                }

                // 3. Garbage Truck (Station Kamikaze Logic)
                val trIt = trucks.iterator()
                while (trIt.hasNext()) {
                    val t = trIt.next(); val target = pathPoints.getOrNull(t.pathIndex - 1)
                    if (target != null) {
                        val dx = target.x - t.x; val dy = target.y - t.y; val d = sqrt(dx*dx + dy*dy)
                        if (d < 15f) t.pathIndex-- else { t.x += (dx/d)*6.5f; t.y += (dy/d)*6.5f } // วิ่งช้าลง

                        for (e in enemies) {
                            if (abs(e.x - t.x) < 55f && abs(e.y - t.y) < 55f) {
                                if (e.hp > t.hp) { // ศัตรูเลือดเยอะกว่า -> หักเลือดทั้งหมดแล้วรถหาย
                                    e.hp -= t.hp; damageTexts.add(DamageText(e.x, e.y, t.hp.toInt().toString()))
                                    t.hp = 0f; break
                                } else { // รถเลือดเยอะกว่า -> ชนทะลุแต่หักเลือดรถ
                                    e.hp -= 350f; t.hp -= 150f
                                    damageTexts.add(DamageText(e.x, e.y, "350"))
                                }
                            }
                        }
                        if (t.hp <= 0) trIt.remove()
                    } else trIt.remove()
                }

                // 4. Tower AI
                towers.forEach { tower ->
                    if (tower.cd > 0) { tower.cd--; return@forEach }
                    if (tower.type == TowerType.STATION) {
                        val tHp = 4500f + (tower.level * 6000f)
                        trucks.add(GarbageTruck(pathPoints.last().x, pathPoints.last().y, tHp, tHp, pathPoints.size - 1))
                        tower.cd = max(400, 1000 - (tower.level * 180)) // ปล่อยช้าลง
                    } else {
                        val range = if (tower.type == TowerType.SNIPER) 100000f else tower.type.range + (tower.level * 60)
                        val target = enemies.filter { distSq(it.x, it.y, tower.x, tower.y) < range*range }.maxByOrNull { it.pathIndex }
                        tower.targetId = target?.id
                        target?.let { t ->
                            val mult = if (tower.type == TowerType.PYRO) 18f else if (tower.type == TowerType.SNIPER) 280f else 90f
                            val dmg = tower.type.baseDamage + (tower.level * mult)
                            when (tower.type) {
                                TowerType.SOLDIER, TowerType.SNIPER -> projectiles.add(Projectile(tower.x, tower.y-40, t.id, dmg, tower.type))
                                TowerType.WIZARD -> projectiles.add(Projectile(tower.x, tower.y-60, t.id, dmg, tower.type, bounces = tower.level, lightningPath = createLightning(Offset(tower.x, tower.y-60), Offset(t.x, t.y))))
                                TowerType.PYRO -> {
                                    t.hp -= dmg; t.burnTicks = 4; t.burnDamage = dmg*0.45f; t.slowTimer = 120
                                }
                                else -> {}
                            }
                            tower.cd = max(4, tower.type.baseCd - (tower.level * 5))
                        }
                    }
                }

                // 5. Projectiles
                val pIt = projectiles.iterator()
                while (pIt.hasNext()) {
                    val p = pIt.next(); val target = enemies.find { it.id == p.targetId }
                    if (target == null || target.hp <= 0) { pIt.remove(); continue }
                    val dx = target.x - p.x; val dy = target.y - p.y; val d = sqrt(dx*dx + dy*dy)
                    if (d < 25f || p.type == TowerType.WIZARD) { // สายฟ้าโดนทันที
                        target.hp -= p.damage; damageTexts.add(DamageText(target.x, target.y, p.damage.toInt().toString()))
                        if (p.type == TowerType.WIZARD && p.bounces > 0) {
                            p.hitEnemies.add(target.id)
                            val next = enemies.find { !p.hitEnemies.contains(it.id) && distSq(it.x, it.y, p.x, p.y) < 400*400 }
                            if (next != null) { p.targetId = next.id; p.bounces--; p.lightningPath = createLightning(Offset(p.x, p.y), Offset(next.x, next.y)); continue }
                        }
                        pIt.remove()
                    } else { p.x += (dx/d)*40f; p.y += (dy/d)*40f }
                }
                damageTexts.removeAll { it.life--; it.y -= 2.5f; it.life <= 0 }
            }
            delay(16)
        }
    }

    // ---------------- UI ----------------

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("💰 $money  WAVE $wave  ❤️ $baseHP", color = Color.White, fontWeight = FontWeight.Bold)
                // ปุ่ม Speed X2
                Button(onClick = { gameSpeed = if(gameSpeed == 1) 2 else 1 }, shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if(gameSpeed == 1) "X1" else "X2", fontSize = 14.sp)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Canvas(modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            val t = towers.find { distSq(it.x, it.y, off.x, off.y) < 3000f }
                            if (t != null) { selectedInGame = t; selectedBuild = null }
                            else if (selectedBuild != null && money >= selectedBuild!!.cost) {
                                towers.add(Tower(x = off.x, y = off.y, type = selectedBuild!!))
                                money -= selectedBuild!!.cost; selectedBuild = null
                            } else selectedInGame = null
                        }
                    }
                ) {
                    if (pathPoints.isEmpty()) {
                        pathPoints = listOf(Offset(0f, size.height*0.4f), Offset(size.width*0.2f, size.height*0.4f), Offset(size.width*0.2f, size.height*0.15f), Offset(size.width*0.5f, size.height*0.15f), Offset(size.width*0.5f, size.height*0.65f), Offset(size.width*0.8f, size.height*0.65f), Offset(size.width*0.8f, size.height*0.35f), Offset(size.width, size.height*0.35f))
                    }
                    drawPath(Path().apply { pathPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }, Color(0xFFDED2A1), style = Stroke(95f))
                    drawHouse(this, pathPoints.last())

                    enemies.forEach { e ->
                        drawStickman(this, Offset(e.x, e.y), e.type.color, e.type.size, anim = e.walkAnim)
                        drawHealthBar(this, e.x, e.y - e.type.size - 15, 50f, e.hp / e.maxHp)
                        // แสดงสัญลักษณ์ Debuff
                        if (e.burnTicks > 0) drawContext.canvas.nativeCanvas.drawText("🔥", e.x - 15, e.y - e.type.size - 30, textPaint)
                        if (e.slowTimer > 0) drawContext.canvas.nativeCanvas.drawText("🛡️", e.x + 15, e.y - e.type.size - 30, textPaint)
                    }

                    trucks.forEach { t -> drawTruck(this, Offset(t.x, t.y), t.hp / t.maxHp) }
                    towers.forEach { t -> drawTowerModel(this, t, enemies.find { it.id == t.targetId }) }

                    projectiles.forEach { p ->
                        if (p.type == TowerType.WIZARD) p.lightningPath?.let { drawLightning(this, it) }
                        else drawCircle(Color.Black, 7f, Offset(p.x, p.y))
                    }
                    damageTexts.forEach { dt -> drawContext.canvas.nativeCanvas.drawText(dt.text, dt.x, dt.y, textPaint) }

                    selectedInGame?.let { tower ->
                        val r = if (tower.type == TowerType.SNIPER) 2000f else tower.type.range + (tower.level * 60f)
                        drawCircle(Color.Blue.copy(0.15f), r, Offset(tower.x, tower.y))
                    }
                }

                // Boss Realtime HP Bar
                enemies.find { it.type == EnemyType.BOSS }?.let { boss ->
                    Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).width(260.dp)) {
                        Text("BOSS HP: ${boss.hp.toInt()}", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        LinearProgressIndicator(progress = { boss.hp / boss.maxHp }, color = Color.Red, modifier = Modifier.fillMaxWidth().height(8.dp))
                    }
                }

                // Upgrade Card
                selectedInGame?.let { tower ->
                    Card(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.9f).padding(bottom = 100.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.85f))) {
                        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${tower.type.label} LV.${tower.level}", color = Color.White, fontWeight = FontWeight.Bold)
                                if (tower.type == TowerType.STATION) {
                                    StatRow("TRUCK HP", 4500f + (tower.level * 6000f), 6000f)
                                    StatRow("SPD (s)", (max(400, 1000 - (tower.level * 180))/60f), -0.5f)
                                } else {
                                    val mult = if (tower.type == TowerType.PYRO) 18f else if (tower.type == TowerType.SNIPER) 280f else 90f
                                    StatRow("DMG", tower.type.baseDamage + (tower.level * mult), mult)
                                    StatRow("SPD (s)", (max(4, tower.type.baseCd - (tower.level * 5))/60f), -0.1f)
                                }
                            }
                            if (tower.level < 3) {
                                val cost = 450 + (tower.level * 350)
                                Button(onClick = { if (money >= cost) { money -= cost; tower.level++ } }) { Text("UP ($$cost)") }
                            }
                        }
                    }
                }

                // Shop Menu
                Row(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(10.dp).align(Alignment.BottomCenter), Arrangement.SpaceEvenly) {
                    TowerType.entries.forEach { type ->
                        Column(modifier = Modifier.clickable { selectedBuild = type; selectedInGame = null }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(50.dp).background(if(selectedBuild == type) Color.DarkGray else Color.White, RoundedCornerShape(10.dp)), Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) { drawTowerModel(this, Tower(x=size.width/2, y=size.height/2+12, type=type), null) }
                            }
                            Text(type.label, color = Color.White, fontSize = 11.sp)
                            Text("$${type.cost}", color = Color.Yellow, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        if (status != GameStatus.PLAYING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
                Button(onClick = { if(status == GameStatus.GAME_OVER) { towers.clear(); enemies.clear(); trucks.clear(); money=1000; wave=0; baseHP=20 }; status = GameStatus.PLAYING }) {
                    Text("START MISSION", fontSize = 20.sp)
                }
            }
        }
    }
}

// ---------------- HELPERS ----------------

@Composable fun StatRow(label: String, cur: Float, plus: Float) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(Color.LightGray)) { append("$label: ") }
        append("%.2f".format(cur))
        if (plus != 0f) withStyle(SpanStyle(if(plus>0) Color.Green else Color.Red)) { append(" (${if(plus>0) "+" else ""}$plus)") }
    }, fontSize = 13.sp, color = Color.White)
}

fun createLightning(start: Offset, end: Offset): List<Offset> {
    val pts = mutableListOf<Offset>()
    pts.add(start)
    val mid1 = Offset(start.x + (end.x - start.x)*0.3f + Random.nextInt(-40, 40), start.y + (end.y - start.y)*0.3f + Random.nextInt(-40, 40))
    val mid2 = Offset(start.x + (end.x - start.x)*0.6f + Random.nextInt(-40, 40), start.y + (end.y - start.y)*0.6f + Random.nextInt(-40, 40))
    pts.add(mid1); pts.add(mid2); pts.add(end)
    return pts
}

fun drawLightning(scope: DrawScope, pts: List<Offset>) {
    for (i in 0 until pts.size - 1) {
        scope.drawLine(Color(0xFFE0B0FF), pts[i], pts[i+1], strokeWidth = 5f)
    }
}

fun drawTowerModel(scope: DrawScope, tower: Tower, target: Enemy?, alpha: Float = 1f) {
    val pos = Offset(tower.x, tower.y); val color = tower.type.color.copy(alpha)
    if (tower.type == TowerType.STATION) {
        scope.drawRect(color, Offset(pos.x-35, pos.y-45), Size(70f, 55f))
    } else {
        drawStickman(scope, pos, color, 30f, true)
        val angle = if (target != null) atan2(target.y - pos.y, target.x - pos.x) else 0f
        when(tower.type) {
            TowerType.SOLDIER -> scope.drawLine(color, Offset(pos.x, pos.y-35), Offset(pos.x + cos(angle)*50, pos.y-35+sin(angle)*50), 8f)
            TowerType.SNIPER -> scope.drawLine(color, Offset(pos.x, pos.y-35), Offset(pos.x + cos(angle)*80, pos.y-35+sin(angle)*80), 12f)
            TowerType.WIZARD -> scope.drawCircle(Color(0xFFF0D0FF).copy(alpha), 14f, Offset(pos.x, pos.y-80))
            TowerType.PYRO -> scope.drawLine(color, Offset(pos.x, pos.y-35), Offset(pos.x + cos(angle)*60, pos.y-35+sin(angle)*60), 15f)
            else -> {}
        }
    }
}

fun drawStickman(scope: DrawScope, center: Offset, color: Color, size: Float, isTower: Boolean = false, anim: Float = 0f) {
    val hR = size * 0.45f; val bH = size * 1.1f; val limb = size * 0.85f
    scope.drawCircle(color, hR, center.copy(y = center.y - bH - hR))
    scope.drawLine(color, center.copy(y = center.y - bH), center, 7f)
    if (isTower) {
        scope.drawLine(color, center, Offset(center.x - limb, center.y + limb*0.5f), 7f)
        scope.drawLine(color, center, Offset(center.x + limb, center.y + limb*0.5f), 7f)
        scope.drawRect(color, Offset(center.x - limb, center.y + limb*0.4f), Size(limb*2, 8f))
    } else {
        val s = sin(anim*3.2f)*limb
        scope.drawLine(color, center, Offset(center.x - s, center.y + limb), 7f)
        scope.drawLine(color, center, Offset(center.x + s, center.y + limb), 7f)
    }
}

fun drawTruck(scope: DrawScope, pos: Offset, hpRatio: Float) {
    scope.drawRect(Color(0xFF78909C), Offset(pos.x-40, pos.y-25), Size(80f, 40f))
    scope.drawRect(Color.Red, Offset(pos.x-35, pos.y-35), Size(70f, 6f))
    scope.drawRect(Color.Green, Offset(pos.x-35, pos.y-35), Size(70f * hpRatio, 6f))
}

fun drawHealthBar(scope: DrawScope, x: Float, y: Float, w: Float, ratio: Float) {
    scope.drawRect(Color.Red, Offset(x-w/2, y), Size(w, 6f))
    scope.drawRect(Color.Green, Offset(x-w/2, y), Size(w * ratio.coerceIn(0f,1f), 6f))
}

fun drawHouse(scope: DrawScope, pos: Offset) {
    scope.drawRect(Color(0xFF5D4037), Offset(pos.x-45, pos.y-45), Size(90f, 90f))
    scope.drawPath(Path().apply { moveTo(pos.x-55, pos.y-45); lineTo(pos.x, pos.y-100); lineTo(pos.x+55, pos.y-45) }, Color.DarkGray)
}

fun distSq(x1: Float, y1: Float, x2: Float, y2: Float) = (x1-x2).pow(2) + (y1-y2).pow(2)