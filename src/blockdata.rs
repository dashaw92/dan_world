#[derive(Debug)]
pub enum DanBlockData {
    Orientation(Axis),
    Age(u8),
    SnowLevel(u8),
    LiquidLevel(u8),
    Bisected(BisectionHalf),
    Direction(Direction),
    Waterlogged(bool),
    Rotation(Direction),
    MultipleFacing(Vec<Direction>),
    Open(bool),
    RailShape(RailShape),
    StairShape(StairShape),
    Attached(bool),
    Hinge(Side),
    Farmland(u8),
}

#[derive(Debug)]
pub enum Side {
    Left,  //0
    Right, //1
}

#[derive(Debug)]
pub enum StairShape {
    InnerLeft,  //000
    InnerRight, //001
    OuterLeft,  //010
    OuterRight, //011
    Straight,   //100
}

#[derive(Debug)]
pub enum BisectionHalf {
    Top,    //0
    Bottom, //1
}

// 2 bits
#[derive(Debug)]
pub enum Axis {
    X, //00
    Y, //01
    Z, //10
}

// 5 bits
#[derive(Debug)]
pub enum Direction {
    Down,           //00000
    East,           //00001
    EastNorthEast,  //00010
    EastSouthEast,  //00011
    North,          //00100
    NorthEast,      //00101
    NorthNorthEast, //00110
    NorthNorthWest, //00111
    NorthWest,      //01000
    South,          //01001
    SouthEast,      //01010
    SouthSouthEast, //01011
    SouthSouthWest, //01100
    SouthWest,      //01101
    Up,             //01110
    West,           //01111
    WestNorthWest,  //10000
    WestSouthWest,  //10001
}

#[derive(Debug)]
pub enum RailShape {
    AscEast,    //0000
    AscNorth,   //0001
    AscSouth,   //0010
    AscWest,    //0011
    EastWest,   //0100
    NorthEast,  //0101
    NorthSouth, //0110
    NorthWest,  //0111
    SouthEast,  //1000
    SouthWest,  //1001
}

pub(crate) fn from(bits: u16) -> Option<DanBlockData> {
    const TYPE_MASK: u16 = 0b11110000_00000000;
    const DATA_MASK: u16 = !TYPE_MASK;

    let prop_type = (bits & TYPE_MASK) >> 12;
    let data = bits & DATA_MASK;

    Some(match prop_type {
        0b0000 => orientation(data),
        0b0001 => age(data),
        0b0010 => snowlevel(data),
        0b0011 => liquidlevel(data),
        0b0100 => bisect(data),
        0b0101 => direction(data),
        0b0110 => waterlogged(data),
        0b0111 => rotation(data),
        0b1000 => multiplefacing(data),
        0b1001 => open(data),
        0b1010 => railshape(data),
        0b1011 => stairshape(data),
        0b1100 => attached(data),
        0b1101 => hinge(data),
        0b1110 => farmland(data),
        _ => return None,
    })
}

fn attached(bits: u16) -> DanBlockData {
    DanBlockData::Attached(bits & 1 == 1)
}

fn hinge(bits: u16) -> DanBlockData {
    DanBlockData::Hinge(match bits & 1 {
        0 => Side::Left,
        1 => Side::Right,
        _ => Side::Left,
    })
}

fn farmland(bits: u16) -> DanBlockData {
    DanBlockData::Farmland((bits & 0xFF) as u8)
}

fn orientation(bits: u16) -> DanBlockData {
    DanBlockData::Orientation(parse_axis(bits))
}

fn age(bits: u16) -> DanBlockData {
    DanBlockData::Age((bits & 0xFF) as u8)
}

fn snowlevel(bits: u16) -> DanBlockData {
    DanBlockData::SnowLevel((bits & 0xFF) as u8)
}

fn liquidlevel(bits: u16) -> DanBlockData {
    DanBlockData::LiquidLevel((bits & 0xFF) as u8)
}

fn bisect(bits: u16) -> DanBlockData {
    DanBlockData::Bisected(match bits & 1 {
        0 => BisectionHalf::Top,
        1 => BisectionHalf::Bottom,
        _ => unreachable!(),
    })
}

fn direction(bits: u16) -> DanBlockData {
    DanBlockData::Direction(parse_direction(bits))
}

fn waterlogged(bits: u16) -> DanBlockData {
    DanBlockData::Waterlogged(bits & 1 == 1)
}

fn rotation(bits: u16) -> DanBlockData {
    DanBlockData::Rotation(parse_direction(bits))
}

fn multiplefacing(bits: u16) -> DanBlockData {
    fn i_to_dir(i: usize) -> Direction {
        match i {
            0 => Direction::North,
            1 => Direction::South,
            2 => Direction::East,
            3 => Direction::West,
            4 => Direction::Up,
            5 => Direction::Down,
            _ => unreachable!(),
        }
    }

    let mut facing = Vec::with_capacity(6);
    let mut current_bit = 1;
    for i in 0..6 {
        if bits & current_bit == current_bit {
            facing.push(i_to_dir(i));
        }

        current_bit <<= 1;
    }

    DanBlockData::MultipleFacing(facing)
}

fn open(bits: u16) -> DanBlockData {
    DanBlockData::Open(bits & 1 == 1)
}

fn railshape(bits: u16) -> DanBlockData {
    DanBlockData::RailShape(match bits & 0b1111 {
        0b0000 => RailShape::AscEast,
        0b0001 => RailShape::AscNorth,
        0b0010 => RailShape::AscSouth,
        0b0011 => RailShape::AscWest,
        0b0100 => RailShape::EastWest,
        0b0101 => RailShape::NorthEast,
        0b0110 => RailShape::NorthSouth,
        0b0111 => RailShape::NorthWest,
        0b1000 => RailShape::SouthEast,
        0b1001 => RailShape::SouthWest,
        _ => RailShape::EastWest,
    })
}

fn stairshape(bits: u16) -> DanBlockData {
    DanBlockData::StairShape(match bits & 0b111 {
        0b000 => StairShape::InnerLeft,
        0b001 => StairShape::InnerRight,
        0b010 => StairShape::OuterLeft,
        0b011 => StairShape::OuterRight,
        0b100 => StairShape::Straight,
        _ => StairShape::Straight,
    })
}

fn parse_axis(bits: u16) -> Axis {
    match bits & 0b11 {
        0b00 => Axis::X,
        0b01 => Axis::Y,
        0b11 => Axis::Z,
        _ => Axis::Y,
    }
}

fn parse_direction(bits: u16) -> Direction {
    match bits & 0b11111 {
        0b00000 => Direction::Down,
        0b00001 => Direction::East,
        0b00010 => Direction::EastNorthEast,
        0b00011 => Direction::EastSouthEast,
        0b00100 => Direction::North,
        0b00101 => Direction::NorthEast,
        0b00110 => Direction::NorthNorthEast,
        0b00111 => Direction::NorthNorthWest,
        0b01000 => Direction::NorthWest,
        0b01001 => Direction::South,
        0b01010 => Direction::SouthEast,
        0b01011 => Direction::SouthSouthEast,
        0b01100 => Direction::SouthSouthWest,
        0b01101 => Direction::SouthWest,
        0b01110 => Direction::Up,
        0b01111 => Direction::West,
        0b10000 => Direction::WestNorthWest,
        0b10001 => Direction::WestSouthWest,
        _ => Direction::North,
    }
}
