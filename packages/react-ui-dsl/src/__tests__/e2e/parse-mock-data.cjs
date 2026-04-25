const fs = require('fs');
const path = require('path');

const inputPath = process.argv[2] || 'mock-data.json';
const outputDir = process.argv[3] || 'json-data';

if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true });
}

const raw = fs.readFileSync(inputPath, 'utf-8');
const arr = JSON.parse(raw);

let success = 0;
arr.forEach((item, index) => {
  let obj;
  if (typeof item === 'string') {
    try {
      obj = JSON.parse(item);
    } catch (e) {
      console.warn(`第 ${index} 项解析失败:`, e.message);
      return;
    }
  } else {
    obj = item;
  }

  const filePath = path.join(outputDir, `${index}.json`);
  fs.writeFileSync(filePath, JSON.stringify(obj, null, 2), 'utf-8');
  success++;
});

console.log(`完成！共输出 ${success} 个文件到 ${outputDir}/ 目录`);