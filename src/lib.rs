#![allow(dead_code)]

use std::{
    collections::HashMap,
    io::{self, Read, Result},
    path::Path,
};

use blockdata::DanBlockData;
use byteorder::{BigEndian, ReadBytesExt};

pub mod biomes;
pub mod blockdata;
pub mod extra;
use biomes::DanBiome;

use extra::DanExtra;
use flate2::read::GzDecoder;

#[derive(Debug)]
pub struct DanWorld {
    pub version: u8,
    pub dimension: DanDimension,
    pub width: u16,
    pub depth: u16,
    pub chunks: Vec<DanChunk>,
    extra: HashMap<String, DanExtra>,
}

#[derive(Debug)]
pub enum DanDimension {
    Overworld,
    Nether,
    End,
}

#[derive(Debug)]
pub struct DanChunk {
    pub x: u16,
    pub z: u16,
    pub sections: Vec<DanChunkSection>,
}

#[derive(Debug)]
pub struct DanChunkSection {
    pub palette: Vec<String>,
    pub blocks: Vec<u8>,
    pub biomes: Vec<DanBiome>,
    pub data: HashMap<(usize, usize, usize), Vec<DanBlockData>>,
}

impl DanWorld {
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let bytes = std::fs::read(path)?;
        let mut gz = GzDecoder::new(&bytes[..]);

        let _magic = read_string(&mut gz)?;
        assert_eq!(&_magic, "DanWorld");
        let version = gz.read_u8()?;

        let dimension = match gz.read_u8()? {
            0 => DanDimension::Overworld,
            1 => DanDimension::Nether,
            2 => DanDimension::End,
            _ => DanDimension::Overworld,
        };

        let width = gz.read_u16::<BigEndian>()?;
        let depth = gz.read_u16::<BigEndian>()?;

        let mut chunks = Vec::with_capacity((width * depth) as usize);

        for _ in 0..chunks.capacity() {
            chunks.push(read_chunk(&mut gz)?);
        }

        let num_extra = gz.read_u16::<BigEndian>()? as usize;
        let mut extra = HashMap::with_capacity(num_extra);
        for _ in 0..num_extra {
            let (key, data) = DanExtra::from_buf(&mut gz)?;
            extra.insert(key, data);
        }

        Ok(Self {
            version,
            dimension,
            width,
            depth,
            chunks,
            extra,
        })
    }

    pub fn get_extra(&self, key: &str) -> Option<&DanExtra> {
        self.extra.get(key)
    }
}

type Cur<'a> = GzDecoder<&'a [u8]>;

fn read_chunk(c: &mut Cur) -> Result<DanChunk> {
    let x = c.read_u16::<BigEndian>()?;
    let z = c.read_u16::<BigEndian>()?;

    let mut sections = Vec::with_capacity(8);

    let num_sections = c.read_u8()?;
    for _ in 0..num_sections {
        sections.push(read_chunk_section(c)?);
    }

    Ok(DanChunk { x, z, sections })
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

    let num_data = c.read_u16::<BigEndian>()? as usize;
    let mut data = HashMap::with_capacity(num_data);
    for _ in 0..num_data {
        let block_data_bits = c.read_u16::<BigEndian>()? as usize;

        let block_x = (block_data_bits & 0b1111_0000_0000_0000) >> 12;
        let block_y = (block_data_bits & 0b0000_1111_0000_0000) >> 8;
        let block_z = (block_data_bits & 0b0000_0000_1111_0000) >> 4;
        let data_len = block_data_bits & 0b0000_0000_0000_1111;
        let mut current_data = Vec::with_capacity(data_len);

        for _ in 0..data_len {
            let Some(current) = blockdata::from(c.read_u16::<BigEndian>()?) else {
                continue;
            };

            current_data.push(current);
        }

        if !current_data.is_empty() {
            data.insert((block_x, block_y, block_z), current_data);
        }
    }

    Ok(DanChunkSection {
        palette,
        blocks,
        biomes,
        data,
    })
}

pub(crate) fn read_string<W: ReadBytesExt>(c: &mut W) -> Result<String> {
    let len = c.read_u8()? as usize;
    let mut buf = vec![0; len];
    c.read_exact(&mut buf)?;

    String::from_utf8(buf).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
}
