package com.example.data

import com.example.data.model.Game

/**
 * Provides categories, thumbnail presets, and an optional starter HTML5 template for the Owner.
 * Pre-installed games have been removed so that ONLY games uploaded by the Owner appear in BloxVerse.
 */
object BuiltInHtml5Games {

    val CATEGORIES = listOf(
        "All",
        "Obby",
        "Tycoon",
        "Simulator",
        "Adventure",
        "Action",
        "Arcade",
        "Puzzle"
    )

    val THUMBNAIL_PRESETS = listOf(
        "obby_neon" to "Neon Obby Arena",
        "cyber_tycoon" to "Cyber Tycoon Core",
        "hyper_sim" to "Hyper Blox Portal",
        "star_raider" to "Starlight Galaxy",
        "voxel_dungeon" to "Voxel Dungeon",
        "prism_puzzle" to "Prism Block Grid"
    )

    /**
     * Returns an empty catalog — only games uploaded by the Owner exist in the platform.
     */
    fun defaultCatalog(ownerUid: String = ""): List<Game> = emptyList()

    fun starterTemplateHtml(title: String = "Custom BloxVerse Game"): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>$title</title>
<style>
  * { box-sizing: border-box; user-select: none; -webkit-user-select: none; }
  html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0B0E17; color: #F8FAFC; font-family: system-ui, sans-serif; overflow: hidden; touch-action: none; }
  #hud { position: fixed; top: 56px; left: 12px; right: 12px; display: flex; justify-content: space-between; pointer-events: none; z-index: 10; gap: 8px; }
  .pill { background: rgba(18,21,38,0.88); border: 1px solid #8B5CF6; border-radius: 999px; padding: 8px 14px; font-weight: 800; font-size: 13px; color: #38BDF8; box-shadow: 0 0 12px rgba(139,92,246,0.4); }
  canvas { display: block; width: 100vw; height: 100vh; }
</style>
</head>
<body>
<div id="hud">
  <div class="pill" id="levelEl">LEVEL: 1</div>
  <div class="pill" id="scoreEl">SCORE: 0</div>
</div>
<canvas id="c"></canvas>
<script>
const canvas = document.getElementById('c');
const ctx = canvas.getContext('2d');
const scoreEl = document.getElementById('scoreEl');
const levelEl = document.getElementById('levelEl');
let w = canvas.width = window.innerWidth;
let h = canvas.height = window.innerHeight;
window.addEventListener('resize', () => { w = canvas.width = window.innerWidth; h = canvas.height = window.innerHeight; });

let score = parseInt(localStorage.getItem('score') || localStorage.getItem('highScore') || '0', 10) || 0;
let level = parseInt(localStorage.getItem('level') || localStorage.getItem('bloxverse_saved_level') || '1', 10) || 1;
window.score = score;
window.level = level;
scoreEl.textContent = 'SCORE: ' + score;
levelEl.textContent = 'LEVEL: ' + level;

let player = { x: w / 2, y: h * 0.75, r: 24 };
let orbs = [];
let particles = [];

function spawnOrb() {
  orbs.push({
    x: 30 + Math.random() * (w - 60),
    y: -20,
    r: 14 + Math.random() * 8,
    vy: (2.2 + Math.random() * 2.2) * (1 + (level - 1) * 0.12),
    color: Math.random() > 0.3 ? '#06B6D4' : '#EC4899'
  });
}

function pointerMove(x, y) {
  player.x = Math.max(player.r, Math.min(w - player.r, x));
  player.y = Math.max(player.r, Math.min(h - player.r, y));
}
window.addEventListener('pointermove', e => pointerMove(e.clientX, e.clientY));
window.addEventListener('pointerdown', e => pointerMove(e.clientX, e.clientY));

function saveProgress() {
  try {
    localStorage.setItem('score', String(score));
    localStorage.setItem('level', String(level));
    if (window.BloxVerseBridge && window.BloxVerseBridge.saveProgress) {
      window.BloxVerseBridge.saveProgress(level, score, JSON.stringify(localStorage));
    }
  } catch (e) {}
}

function loop() {
  ctx.fillStyle = '#0B0E17';
  ctx.fillRect(0, 0, w, h);

  if (Math.random() < 0.05) spawnOrb();

  ctx.save();
  ctx.shadowBlur = 20;
  ctx.shadowColor = '#8B5CF6';
  ctx.fillStyle = '#8B5CF6';
  ctx.fillRect(player.x - player.r, player.y - player.r, player.r * 2, player.r * 2);
  ctx.restore();

  for (let i = orbs.length - 1; i >= 0; i--) {
    const o = orbs[i];
    o.y += o.vy;
    ctx.beginPath();
    ctx.arc(o.x, o.y, o.r, 0, Math.PI * 2);
    ctx.fillStyle = o.color;
    ctx.fill();

    const dx = o.x - player.x;
    const dy = o.y - player.y;
    if (Math.hypot(dx, dy) < o.r + player.r) {
      score += 10;
      const newLevel = Math.max(level, Math.floor(score / 100) + 1);
      level = newLevel;
      window.score = score;
      window.level = level;
      scoreEl.textContent = 'SCORE: ' + score;
      levelEl.textContent = 'LEVEL: ' + level;
      saveProgress();
      for (let p = 0; p < 10; p++) {
        particles.push({ x: o.x, y: o.y, vx: (Math.random()-0.5)*6, vy: (Math.random()-0.5)*6, life: 25, color: o.color });
      }
      orbs.splice(i, 1);
    } else if (o.y > h + 40) {
      orbs.splice(i, 1);
    }
  }

  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx; p.y += p.vy; p.life--;
    ctx.fillStyle = p.color;
    ctx.fillRect(p.x, p.y, 4, 4);
    if (p.life <= 0) particles.splice(i, 1);
  }
  requestAnimationFrame(loop);
}
loop();
</script>
</body>
</html>
""".trimIndent()
}
