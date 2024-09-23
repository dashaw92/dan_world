use std::io::Cursor;

use anyhow::Context;
use anyhow::Result;
use byteorder::BigEndian;
use byteorder::ReadBytesExt;

use crate::read_string;

#[derive(Debug)]
pub struct DanExtra {
    pub data: Vec<u8>,
}

impl DanExtra {
    pub(crate) fn from_buf<R: byteorder::ReadBytesExt>(r: &mut R) -> Result<(String, DanExtra)> {
        let key = read_string(r).context("extra key")?;
        let len = r.read_u16::<BigEndian>().context("extra data len")? as usize;
        let mut data = Vec::with_capacity(len);
        for _ in 0..len {
            data.push(r.read_u8().context("byte for extra data")?);
        }

        Ok((key, DanExtra { data }))
    }

    pub fn to_coords(&self) -> anyhow::Result<[f64; 5]> {
        let mut cursor = Cursor::new(&self.data);
        let mut buf = [0.0; 5];

        for i in 0..3 {
            buf[i] = cursor
                .read_f64::<BigEndian>()
                .context("Expected bytes to be floats")?;
        }

        for i in 3..5 {
            buf[i] = cursor
                .read_f32::<BigEndian>()
                .context("Reading yaw and pitch in extra")? as f64;
        }

        Ok(buf)
    }

    pub fn to_string(&self) -> anyhow::Result<String> {
        let mut cursor = Cursor::new(&self.data);

        let len = cursor
            .read_u32::<BigEndian>()
            .context("Reading string length from extra")? as usize;
        let mut buf = Vec::with_capacity(len);
        for _ in 0..len {
            buf.push(
                cursor
                    .read_u8()
                    .context("reading byte from string from extra")?,
            );
        }

        String::from_utf8(buf).context("Converting raw UTF-8 bytes to a string from extra")
    }
}
