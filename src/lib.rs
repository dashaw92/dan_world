#![allow(dead_code)]

use std::{
    io::{self, Cursor, Read, Result},
    path::Path,
};

use byteorder::{BigEndian, ReadBytesExt};

pub struct DanWorld {
    version: u8,
    width: u16,
    depth: u16,
    chunk_mask: Vec<u8>,
    chunks: Vec<DanChunk>,
}

pub struct DanChunk {
    heights: [u16; 256],
    biomes: [DanBiome; 256],
    sections: [DanChunkSection; 8],
}

#[derive(Copy, Clone, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub enum DanBiome {
    Plains,
}

impl From<u16> for DanBiome {
    fn from(_value: u16) -> Self {
        DanBiome::Plains
    }
}

pub struct DanChunkSection {
    y: u8,
    palette: Vec<String>,
    blocks: [u8; 4096],
}

impl DanWorld {
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let bytes = std::fs::read(path)?;
        let mut cursor = Cursor::new(bytes);

        let version = cursor.read_u8()?;
        let width = cursor.read_u16::<BigEndian>()?;
        let depth = cursor.read_u16::<BigEndian>()?;

        let bitmask_len = (width * depth).div_ceil(8) as usize;
        let mut bitmask = Vec::with_capacity(bitmask_len);
        cursor.read_exact(&mut bitmask)?;

        let mut chunks = Vec::with_capacity(bitmask_len * 8);

        for _ in 0..chunks.len() {
            chunks.push(read_chunk(&mut cursor)?);
        }

        Ok(Self {
            version,
            width,
            depth,
            chunk_mask: bitmask,
            chunks,
        })
    }
}

type Cur = Cursor<Vec<u8>>;

fn read_chunk(c: &mut Cur) -> Result<DanChunk> {
    let mut heights = [0u16; 256];
    let mut biomes = [DanBiome::Plains; 256];
    let mut sections = Vec::with_capacity(8);

    for i in 0..256 {
        heights[i] = c.read_u16::<BigEndian>()?;
    }

    for i in 0..256 {
        biomes[i] = DanBiome::from(c.read_u16::<BigEndian>()?);
    }

    let num_sections = c.read_u8()?;
    for _ in 0..num_sections {
        sections.push(read_chunk_section(c)?);
    }

    let sections = std::array::from_fn(|_| sections.pop().expect("there to be 8 sections"));

    Ok(DanChunk {
        heights,
        biomes,
        sections,
    })
}

fn read_chunk_section(c: &mut Cur) -> Result<DanChunkSection> {
    let y = c.read_u8()?;
    let palette_len = c.read_u8()?;
    let mut palette = Vec::with_capacity(palette_len as usize);

    for _ in 0..palette_len {
        palette.push(read_string(c)?);
    }

    let mut blocks = [0u8; 4096];
    c.read_exact(&mut blocks)?;

    Ok(DanChunkSection { y, palette, blocks })
}

fn read_string(c: &mut Cur) -> Result<String> {
    let len = c.read_u8()?;
    let mut buf = Vec::with_capacity(len as usize);
    c.read_exact(&mut buf)?;

    String::from_utf8(buf).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
}
