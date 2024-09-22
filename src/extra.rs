use byteorder::BigEndian;

use crate::read_string;

#[derive(Debug)]
pub struct DanExtra {
    pub data: Vec<u8>,
}

impl DanExtra {
    pub(crate) fn from_buf<R: byteorder::ReadBytesExt>(
        r: &mut R,
    ) -> std::io::Result<(String, DanExtra)> {
        let key = read_string(r)?;
        let len = r.read_u16::<BigEndian>()? as usize;
        let mut data = Vec::with_capacity(len);
        for _ in 0..len {
            data.push(r.read_u8()?);
        }

        Ok((key, DanExtra { data }))
    }
}
