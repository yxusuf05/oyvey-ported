// Assembles a resource-pack zip from the project's workspace files
// (pack.mcmeta, pack.png, assets/**). Returns the finished zip as a Buffer.
import archiver from 'archiver';

export function packResourcePack(files, { description, packFormat }) {
  return new Promise((resolve, reject) => {
    const archive = archiver('zip', { zlib: { level: 9 } });
    const chunks = [];
    archive.on('data', (c) => chunks.push(c));
    archive.on('error', reject);
    archive.on('end', () => resolve(Buffer.concat(chunks)));

    let hasMcmeta = false;
    for (const [relPath, content] of files) {
      if (!/^(assets\/|pack\.mcmeta$|pack\.png$)/.test(relPath)) continue;
      if (relPath === 'pack.mcmeta') hasMcmeta = true;
      archive.append(Buffer.isBuffer(content) ? content : Buffer.from(content, 'utf8'), { name: relPath });
    }
    if (!hasMcmeta) {
      // pack_format for older clients, min/max_format for 1.21.9+ (25w31a).
      archive.append(JSON.stringify({
        pack: { pack_format: packFormat, min_format: packFormat, max_format: packFormat, description },
      }, null, 2), { name: 'pack.mcmeta' });
    }
    archive.finalize();
  });
}
