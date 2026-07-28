(module
  (memory (export "memory") 1)
  (func (export "init"))
  (func (export "bits") (result i64)
    (local $a i64)
    i64.const 0x00ff00ff00ff00ff local.set $a
    local.get $a i64.clz
    local.get $a i64.ctz     i64.add
    local.get $a i64.popcnt  i64.add
    local.get $a i64.const 7 i64.rotl  i64.add
    local.get $a i64.const 3 i64.rotr  i64.add
    i32.const 5 i32.clz i64.extend_i32_u i64.add
    i32.const 5 i32.popcnt i64.extend_i32_u i64.add
    i32.const 9 i32.const 4 i32.rotl i64.extend_i32_u i64.add)
  (func (export "floats") (result f64)
    f64.const 3.5 f64.sqrt
    f64.const -2.25 f64.abs f64.add
    f64.const 7.75 f64.nearest f64.add
    f64.const 1.5 f64.const 2.5 f64.min f64.add
    f64.const 1.5 f64.const 2.5 f64.copysign f64.add
    f32.const 2.5 f32.trunc f64.promote_f32 f64.add)
  (func (export "divs") (result i32)
    i32.const -7 i32.const 2 i32.div_s
    i32.const 7 i32.const 2 i32.rem_u i32.add
    i64.const -9 i64.const 4 i64.div_s i32.wrap_i64 i32.add)
)
