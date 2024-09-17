use dan_world::DanWorld;

fn main() {
    let file = std::env::args()
        .nth(1)
        .expect("Expecting a path to a DanWorld file.");
    _ = dbg!(DanWorld::load(file));
}
