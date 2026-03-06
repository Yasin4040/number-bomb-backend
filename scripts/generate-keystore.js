/**
 * 生成后端 SSL Keystore（PKCS12 格式）
 * 用于 Spring Boot HTTPS 配置
 * 运行: node scripts/generate-keystore.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const resourcesDir = path.join(__dirname, '..', 'src', 'main', 'resources');
const keystorePath = path.join(resourcesDir, 'keystore.p12');

// 检查是否已存在
if (fs.existsSync(keystorePath)) {
  console.log('✅ Keystore 已存在:', keystorePath);
  process.exit(0);
}

console.log('🔐 正在生成后端 SSL Keystore...\n');

try {
  // 使用 keytool (JDK 自带) 生成 keystore
  const keytoolCmd = [
    'keytool',
    '-genkeypair',
    '-alias numberbomb',
    '-keyalg RSA',
    '-keysize 2048',
    '-storetype PKCS12',
    '-keystore', `"${keystorePath}"`,
    '-validity 365',
    '-storepass numberbomb',
    '-keypass numberbomb',
    '-dname "CN=192.168.10.100, OU=Dev, O=NumberBomb, L=Beijing, ST=Beijing, C=CN"',
    '-ext "SAN=ip:192.168.10.100,ip:127.0.0.1,dns:localhost"'
  ].join(' ');

  console.log('  执行命令:', keytoolCmd);
  execSync(keytoolCmd, { stdio: 'inherit' });

  console.log('\n✅ Keystore 生成成功！');
  console.log('  路径:', keystorePath);
  console.log('  密码: numberbomb');
  console.log('\n🚀 启动后端 HTTPS 服务:');
  console.log('  mvn spring-boot:run -Dspring-boot.run.profiles=ssl');

} catch (error) {
  console.error('\n❌ 生成 Keystore 失败:', error.message);
  console.log('\n💡 请确保:');
  console.log('  1. 已安装 JDK (keytool 命令可用)');
  console.log('  2. 目录有写入权限');
  process.exit(1);
}
