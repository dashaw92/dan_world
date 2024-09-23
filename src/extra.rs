use anyhow::Context;
use anyhow::Result;
use byteorder::BigEndian;

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
}
