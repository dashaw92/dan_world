#![allow(dead_code)]

use std::{
    io::{self, Read, Result},
    path::Path,
};

use byteorder::{BigEndian, ReadBytesExt};

mod biomes;
use biomes::DanBiome;
use flate2::read::GzDecoder;

#[derive(Debug)]
pub struct DanWorld {
    version: u8,
    width: u16,
    depth: u16,
    chunks: Vec<DanChunk>,
}

#[derive(Debug)]
pub struct DanChunk {
    sections: Vec<DanChunkSection>,
}

#[derive(Debug)]
pub struct DanChunkSection {
    palette: Vec<String>,
    blocks: Vec<u8>,
    biomes: Vec<DanBiome>,
}

impl DanWorld {
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let bytes = std::fs::read(path)?;
        let mut gz = GzDecoder::new(&bytes[..]);

        let _magic = read_string(&mut gz)?;
        assert_eq!(&_magic, "DanWorld");
        let version = gz.read_u8()?;
        let width = gz.read_u16::<BigEndian>()?;
        let depth = gz.read_u16::<BigEndian>()?;

        let mut chunks = Vec::with_capacity((width * depth) as usize);

        for _ in 0..chunks.capacity() {
            chunks.push(read_chunk(&mut gz)?);
        }

        Ok(Self {
            version,
            width,
            depth,
            chunks,
        })
    }
}

type Cur<'a> = GzDecoder<&'a [u8]>;

fn read_chunk(c: &mut Cur) -> Result<DanChunk> {
    let mut sections = Vec::with_capacity(8);

    let num_sections = c.read_u8()?;
    for _ in 0..num_sections {
        sections.push(read_chunk_section(c)?);
    }

    Ok(DanChunk { sections })
}

fn read_chunk_section(c: &mut Cur) -> Result<DanChunkSection> {
    let palette_len = c.read_u8()?;
    let mut palette = Vec::with_capacity(palette_len as usize);

    for _ in 0..palette_len {
        palette.push(read_string(c)?);
    }

    let num_blocks = c.read_u16::<BigEndian>()? as usize;
    let mut blocks = vec![0u8; num_blocks];
    c.read_exact(&mut blocks)?;

    let mut biomes = vec![0u8; num_blocks];
    c.read_exact(&mut biomes)?;
    let biomes = biomes.into_iter().map(DanBiome::from).collect();

    Ok(DanChunkSection {
        palette,
        blocks,
        biomes,
    })
}

fn read_string(c: &mut Cur) -> Result<String> {
    let len = c.read_u8()? as usize;
    let mut buf = vec![0; len];
    c.read_exact(&mut buf)?;

    String::from_utf8(buf).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
}
