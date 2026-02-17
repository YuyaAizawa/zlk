package zlk;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;

public class ConstrainerTest {
	@Test
	void selfRecursiveFunction() {
		String src ="""
		fact n =
		  if isZero n then
		    1
		  else
		    let
		      one = 1
		      nn = sub n one
		    in
		      mul n (fact nn)
		""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.fact: [0],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[1], [2]]
				          header: {
				            Main.fact.n: [1],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[3]]
				                  cons: [
				                    Exists:
				                      vars: [[4], [5], [6]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [4],
				                        [4] = [5] -> [6],
				                        Local: Main.fact.n = [5],
				                        [6] = Bool,
				                      ],
				                    Exists:
				                      vars: [[7], [8]]
				                      cons: [
				                        I32 = [7],
				                        [7] = [8],
				                        [8] = [3],
				                      ],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {
				                        Main.fact.nn: [17],
				                        Main.fact.one: [16],
				                      }
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Let:
				                              rigids: []
				                              flexes: [[18]]
				                              header: {}
				                              headerCons: [
				                                Phase:
				                                  cons: [
				                                    Exists:
				                                      vars: [[19], [20]]
				                                      cons: [
				                                        I32 = [19],
				                                        [19] = [20],
				                                        [20] = [18],
				                                      ],
				                                  ]
				                                  genTargets: [],
				                              ]
				                              bodyCons:[
				                                [18] = [16],
				                              ],
				                          ]
				                          genTargets: [Main.fact.one],
				                        Phase:
				                          cons: [
				                            Let:
				                              rigids: []
				                              flexes: [[21]]
				                              header: {}
				                              headerCons: [
				                                Phase:
				                                  cons: [
				                                    Exists:
				                                      vars: [[22], [23], [24], [25]]
				                                      cons: [
				                                        Foreign: Basic.sub:I32 -> I32 -> I32 = [22],
				                                        [22] = [23] -> [24] -> [25],
				                                        Local: Main.fact.n = [23],
				                                        Local: Main.fact.one = [24],
				                                        [25] = [21],
				                                      ],
				                                  ]
				                                  genTargets: [],
				                              ]
				                              bodyCons:[
				                                [21] = [17],
				                              ],
				                          ]
				                          genTargets: [Main.fact.nn],
				                      ]
				                      bodyCons:[
				                        Exists:
				                          vars: [[9], [10], [11], [15]]
				                          cons: [
				                            Foreign: Basic.mul:I32 -> I32 -> I32 = [9],
				                            [9] = [10] -> [11] -> [15],
				                            Local: Main.fact.n = [10],
				                            Exists:
				                              vars: [[12], [13], [14]]
				                              cons: [
				                                Local: Main.fact = [12],
				                                [12] = [13] -> [14],
				                                Local: Main.fact.nn = [13],
				                                [14] = [11],
				                              ],
				                            [15] = [3],
				                          ],
				                      ],
				                    [3] = [2],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [1] -> [2] = [0],
				          ],
				      ]
				      genTargets: [Main.fact],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void mutualRecursiveFunction() {
		String src ="""
		isEven n =
		  if isZero n then
		    True
		  else
		    isOdd (sub n 1)

		isOdd n =
		  if isZero n then
		    False
		  else
		    isEven (sub n 1)
		""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.isEven: [0],
				    Main.isOdd: [1],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[17], [18]]
				          header: {
				            Main.isOdd.n: [17],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[19]]
				                  cons: [
				                    Exists:
				                      vars: [[20], [21], [22]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [20],
				                        [20] = [21] -> [22],
				                        Local: Main.isOdd.n = [21],
				                        [22] = Bool,
				                      ],
				                    Exists:
				                      vars: [[23], [24]]
				                      cons: [
				                        Foreign: Basic.False:Bool = [23],
				                        [23] = [24],
				                        [24] = [19],
				                      ],
				                    Exists:
				                      vars: [[25], [26], [31]]
				                      cons: [
				                        Local: Main.isEven = [25],
				                        [25] = [26] -> [31],
				                        Exists:
				                          vars: [[27], [28], [29], [30]]
				                          cons: [
				                            Foreign: Basic.sub:I32 -> I32 -> I32 = [27],
				                            [27] = [28] -> [29] -> [30],
				                            Local: Main.isOdd.n = [28],
				                            I32 = [29],
				                            [30] = [26],
				                          ],
				                        [31] = [19],
				                      ],
				                    [19] = [18],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [17] -> [18] = [1],
				          ],
				        Let:
				          rigids: []
				          flexes: [[2], [3]]
				          header: {
				            Main.isEven.n: [2],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[4]]
				                  cons: [
				                    Exists:
				                      vars: [[5], [6], [7]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [5],
				                        [5] = [6] -> [7],
				                        Local: Main.isEven.n = [6],
				                        [7] = Bool,
				                      ],
				                    Exists:
				                      vars: [[8], [9]]
				                      cons: [
				                        Foreign: Basic.True:Bool = [8],
				                        [8] = [9],
				                        [9] = [4],
				                      ],
				                    Exists:
				                      vars: [[10], [11], [16]]
				                      cons: [
				                        Local: Main.isOdd = [10],
				                        [10] = [11] -> [16],
				                        Exists:
				                          vars: [[12], [13], [14], [15]]
				                          cons: [
				                            Foreign: Basic.sub:I32 -> I32 -> I32 = [12],
				                            [12] = [13] -> [14] -> [15],
				                            Local: Main.isEven.n = [13],
				                            I32 = [14],
				                            [15] = [11],
				                          ],
				                        [16] = [4],
				                      ],
				                    [4] = [3],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [2] -> [3] = [0],
				          ],
				      ]
				      genTargets: [Main.isOdd, Main.isEven],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void genericTypeInLetExp() {
		String src ="""
				type IntList =
				  | Nil
				  | Cons I32 IntList

				car list =
				  case list of
				    Nil ->
				      0
				    Cons hd tl ->
				      hd

				rectest =
				  let
				    id x =
				      x
				    res =
				      Cons (id 1) (Cons (car (id (Cons 2 Nil))) Nil)
				  in
				    res
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.car: [0],
				    Main.rectest: [1],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[2], [3]]
				          header: {
				            Main.car.list: [2],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[4], [7]]
				                  cons: [
				                    Exists:
				                      vars: [[5], [6]]
				                      cons: [
				                        Local: Main.car.list = [5],
				                        [5] = [6],
				                        [6] = [4],
				                      ],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {}
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Main.IntList = [4],
				                            Exists:
				                              vars: [[8], [9]]
				                              cons: [
				                                I32 = [8],
				                                [8] = [9],
				                                [9] = [7],
				                              ],
				                          ]
				                          genTargets: [],
				                      ]
				                      bodyCons:[
				                        [7] = [7],
				                      ],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {
				                        Main.car._1.hd: I32,
				                        Main.car._1.tl: Main.IntList,
				                      }
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Main.IntList = [4],
				                            Exists:
				                              vars: [[10], [11]]
				                              cons: [
				                                Local: Main.car._1.hd = [10],
				                                [10] = [11],
				                                [11] = [7],
				                              ],
				                          ]
				                          genTargets: [],
				                      ]
				                      bodyCons:[
				                        [7] = [7],
				                      ],
				                    [7] = [3],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [2] -> [3] = [0],
				          ],
				      ]
				      genTargets: [Main.car],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[12]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Let:
				                  rigids: []
				                  flexes: []
				                  header: {
				                    Main.rectest.id: [15],
				                    Main.rectest.res: [16],
				                  }
				                  headerCons: [
				                    Phase:
				                      cons: [
				                        Let:
				                          rigids: []
				                          flexes: [[17], [18]]
				                          header: {
				                            Main.rectest.id.x: [17],
				                          }
				                          headerCons: [
				                            Phase:
				                              cons: [
				                                Exists:
				                                  vars: [[19], [20]]
				                                  cons: [
				                                    Local: Main.rectest.id.x = [19],
				                                    [19] = [20],
				                                    [20] = [18],
				                                  ],
				                              ]
				                              genTargets: [],
				                          ]
				                          bodyCons:[
				                            [17] -> [18] = [15],
				                          ],
				                      ]
				                      genTargets: [Main.rectest.id],
				                    Phase:
				                      cons: [
				                        Let:
				                          rigids: []
				                          flexes: [[21]]
				                          header: {}
				                          headerCons: [
				                            Phase:
				                              cons: [
				                                Exists:
				                                  vars: [[22], [23], [27], [42]]
				                                  cons: [
				                                    Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [22],
				                                    [22] = [23] -> [27] -> [42],
				                                    Exists:
				                                      vars: [[24], [25], [26]]
				                                      cons: [
				                                        Local: Main.rectest.id = [24],
				                                        [24] = [25] -> [26],
				                                        I32 = [25],
				                                        [26] = [23],
				                                      ],
				                                    Exists:
				                                      vars: [[28], [29], [40], [41]]
				                                      cons: [
				                                        Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [28],
				                                        [28] = [29] -> [40] -> [41],
				                                        Exists:
				                                          vars: [[30], [31], [39]]
				                                          cons: [
				                                            Local: Main.car = [30],
				                                            [30] = [31] -> [39],
				                                            Exists:
				                                              vars: [[32], [33], [38]]
				                                              cons: [
				                                                Local: Main.rectest.id = [32],
				                                                [32] = [33] -> [38],
				                                                Exists:
				                                                  vars: [[34], [35], [36], [37]]
				                                                  cons: [
				                                                    Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [34],
				                                                    [34] = [35] -> [36] -> [37],
				                                                    I32 = [35],
				                                                    Foreign: Main.Nil:Main.IntList = [36],
				                                                    [37] = [33],
				                                                  ],
				                                                [38] = [31],
				                                              ],
				                                            [39] = [29],
				                                          ],
				                                        Foreign: Main.Nil:Main.IntList = [40],
				                                        [41] = [27],
				                                      ],
				                                    [42] = [21],
				                                  ],
				                              ]
				                              genTargets: [],
				                          ]
				                          bodyCons:[
				                            [21] = [16],
				                          ],
				                      ]
				                      genTargets: [Main.rectest.res],
				                  ]
				                  bodyCons:[
				                    Exists:
				                      vars: [[13], [14]]
				                      cons: [
				                        Local: Main.rectest.res = [13],
				                        [13] = [14],
				                        [14] = [12],
				                      ],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [12] = [1],
				          ],
				      ]
				      genTargets: [Main.rectest],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void leakOuterStruct() {
		String src ="""
				pair a b s = s a b
				fst p = p fst_
				fst_ x y = x
				snd p = p snd_
				snd_ x y = y

				id x = x

				p = pair id id

				u = fst p
				v = snd p

				r1 = u 1
				r2 = v True
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.fst: [1],
				    Main.fst_: [2],
				    Main.id: [5],
				    Main.p: [6],
				    Main.pair: [0],
				    Main.r1: [9],
				    Main.r2: [10],
				    Main.snd: [3],
				    Main.snd_: [4],
				    Main.u: [7],
				    Main.v: [8],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[11], [12], [13], [14]]
				          header: {
				            Main.pair.a: [11],
				            Main.pair.b: [12],
				            Main.pair.s: [13],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[15], [16], [17], [18]]
				                  cons: [
				                    Local: Main.pair.s = [15],
				                    [15] = [16] -> [17] -> [18],
				                    Local: Main.pair.a = [16],
				                    Local: Main.pair.b = [17],
				                    [18] = [14],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [11] -> [12] -> [13] -> [14] = [0],
				          ],
				      ]
				      genTargets: [Main.pair],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[34], [35], [36]]
				          header: {
				            Main.snd_.x: [34],
				            Main.snd_.y: [35],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[37], [38]]
				                  cons: [
				                    Local: Main.snd_.y = [37],
				                    [37] = [38],
				                    [38] = [36],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [34] -> [35] -> [36] = [4],
				          ],
				      ]
				      genTargets: [Main.snd_],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[29], [30]]
				          header: {
				            Main.snd.p: [29],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[31], [32], [33]]
				                  cons: [
				                    Local: Main.snd.p = [31],
				                    [31] = [32] -> [33],
				                    Local: Main.snd_ = [32],
				                    [33] = [30],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [29] -> [30] = [3],
				          ],
				      ]
				      genTargets: [Main.snd],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[39], [40]]
				          header: {
				            Main.id.x: [39],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[41], [42]]
				                  cons: [
				                    Local: Main.id.x = [41],
				                    [41] = [42],
				                    [42] = [40],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [39] -> [40] = [5],
				          ],
				      ]
				      genTargets: [Main.id],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[43]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[44], [45], [46], [47]]
				                  cons: [
				                    Local: Main.pair = [44],
				                    [44] = [45] -> [46] -> [47],
				                    Local: Main.id = [45],
				                    Local: Main.id = [46],
				                    [47] = [43],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [43] = [6],
				          ],
				      ]
				      genTargets: [Main.p],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[52]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[53], [54], [55]]
				                  cons: [
				                    Local: Main.snd = [53],
				                    [53] = [54] -> [55],
				                    Local: Main.p = [54],
				                    [55] = [52],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [52] = [8],
				          ],
				      ]
				      genTargets: [Main.v],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[60]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[61], [62], [63]]
				                  cons: [
				                    Local: Main.v = [61],
				                    [61] = [62] -> [63],
				                    Foreign: Basic.True:Bool = [62],
				                    [63] = [60],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [60] = [10],
				          ],
				      ]
				      genTargets: [Main.r2],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[24], [25], [26]]
				          header: {
				            Main.fst_.x: [24],
				            Main.fst_.y: [25],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[27], [28]]
				                  cons: [
				                    Local: Main.fst_.x = [27],
				                    [27] = [28],
				                    [28] = [26],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [24] -> [25] -> [26] = [2],
				          ],
				      ]
				      genTargets: [Main.fst_],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[19], [20]]
				          header: {
				            Main.fst.p: [19],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[21], [22], [23]]
				                  cons: [
				                    Local: Main.fst.p = [21],
				                    [21] = [22] -> [23],
				                    Local: Main.fst_ = [22],
				                    [23] = [20],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [19] -> [20] = [1],
				          ],
				      ]
				      genTargets: [Main.fst],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[48]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[49], [50], [51]]
				                  cons: [
				                    Local: Main.fst = [49],
				                    [49] = [50] -> [51],
				                    Local: Main.p = [50],
				                    [51] = [48],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [48] = [7],
				          ],
				      ]
				      genTargets: [Main.u],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[56]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[57], [58], [59]]
				                  cons: [
				                    Local: Main.u = [57],
				                    [57] = [58] -> [59],
				                    I32 = [58],
				                    [59] = [56],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [56] = [9],
				          ],
				      ]
				      genTargets: [Main.r1],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}
}
