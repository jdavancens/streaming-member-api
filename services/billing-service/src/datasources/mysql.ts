import mysql from 'mysql2/promise';

let pool: mysql.Pool | null = null;

export function getPool(): mysql.Pool {
  if (!pool) {
    pool = mysql.createPool({
      host: process.env.MYSQL_HOST ?? 'localhost',
      port: parseInt(process.env.MYSQL_PORT ?? '3306', 10),
      user: process.env.MYSQL_USER ?? 'billing',
      password: process.env.MYSQL_PASSWORD ?? 'billingpass',
      database: process.env.MYSQL_DATABASE ?? 'billing',
      waitForConnections: true,
      connectionLimit: 10,
    });
  }
  return pool;
}

export async function initSchema(): Promise<void> {
  const db = getPool();
  await db.query(`
    CREATE TABLE IF NOT EXISTS plans (
      id VARCHAR(36) PRIMARY KEY,
      name ENUM('MOBILE','BASIC','STANDARD','PREMIUM') NOT NULL,
      monthly_price DECIMAL(10,2) NOT NULL,
      max_streams INT NOT NULL,
      max_downloads INT NOT NULL,
      video_quality ENUM('SD','HD','UHD') NOT NULL
    )
  `);
  await db.query(`
    CREATE TABLE IF NOT EXISTS subscriptions (
      id VARCHAR(36) PRIMARY KEY,
      member_id VARCHAR(36) NOT NULL,
      plan_id VARCHAR(36) NOT NULL,
      status ENUM('TRIALING','ACTIVE','CANCELLED','PAST_DUE') NOT NULL DEFAULT 'ACTIVE',
      period_start DATETIME NOT NULL,
      period_end DATETIME NOT NULL,
      cancelled_at DATETIME,
      INDEX idx_member (member_id)
    )
  `);

  // Seed default plans if none exist
  const [rows] = await db.query<mysql.RowDataPacket[]>('SELECT COUNT(*) as cnt FROM plans');
  if (rows[0].cnt === 0) {
    await db.query(`
      INSERT INTO plans (id, name, monthly_price, max_streams, max_downloads, video_quality) VALUES
        ('plan-mobile',   'MOBILE',   6.99,  1, 0,  'SD'),
        ('plan-basic',    'BASIC',    9.99,  2, 10, 'HD'),
        ('plan-standard', 'STANDARD', 13.99, 2, 10, 'HD'),
        ('plan-premium',  'PREMIUM',  17.99, 4, 25, 'UHD')
    `);
  }
}
