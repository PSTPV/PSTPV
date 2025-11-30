import subprocess
import re
import json
import argparse
import concurrent.futures
from typing import List, Set, Optional
from dataclasses import dataclass
import pickle

from z3 import *
import ast

start_time = 0
# Create the Z3 solver
solver = Solver()

RESOURCE_DIR = "resources"
RUNNABLE_DOR= "dataset/runnable"
UNHANDLED_ERROR = "Unhandled error"
TESTCASE_GENERATION_RESULT = "Testcase generation result"
CSC_CHECKING_RESULT = "result"
TBFV_PS_MAX_NUM_IN_ONE_PATH = 10
TBFV_PS_TOO_MANY_ERROR_FLAG = "TOO_MANY_ERROR"


def print_verification_timeout_result():
    result = Result(-3, "OVERTIME", "")
    print(f"result:" + result.to_json())
def print_verification_unexpected_result():
    result = Result(-1,"","")
    print(f"result:" + result.to_json())
def print_unknown_variable_result(unknown_variable: str):
    result = Result(-1,unknown_variable,"")

def get_class_name(java_code: str):
    match = re.search(r'class\s+(\w+)', java_code)
    if match:
        return match.group(1)
    else:
        return None

def run_java_code(java_code: str, timeout_seconds=20):
    classname = get_class_name(java_code)
    file_path = RUNNABLE_DOR + "/"  + classname + ".java"
    with open(file_path, "w") as file:
        file.write(java_code)
    try:
        subprocess.run(["javac", file_path], check=True)
    except subprocess.CalledProcessError:
        print("Error during Java compilation.")
        return ""
    try:
        result = subprocess.run(
            ["java", file_path],
            capture_output=True,
            text=True,
            timeout=timeout_seconds
        )
        # print(" result.stdout:" + result.stdout)
        return result
    except subprocess.TimeoutExpired:
        print("Java execution timeout!")
        raise
    except subprocess.CalledProcessError:
        print("Error during Java execution.")
        raise

def parse_execution_path(execution_output: str) -> List[str]:
    lines = execution_output.splitlines()
    execution_path = []

    for line in lines:
        if "current value" in line or "Entering loop" in line or "Exiting loop" in line or "Evaluating if condition" in line \
                or "Return statement" in line or "Function input" in line or "Entering forloop" in line \
                or "Exiting forloop" in line or ("Under condition" in line and "true" in line) or ("REP" in line) \
                or "NP detecting: " in line:
            execution_path.append(line)


    return execution_path

def combind_expr_and_list(expr: str, exprList: List[str]):
    #默认T和preCts中的Ct都是用()包围起来的
    com_expr = expr
    for ct in exprList:
        com_expr = f"{com_expr} && !({ct})"
    return com_expr.strip().strip("&&")

def replace_variables(current_condition: str, variable: str, new_value: str) -> str:
    """
    Replace the variable in the logical condition with the new value.
    """
    pattern = rf'\b{re.escape(variable)}\b'  # Match variable names exactly
    new_value = f"{new_value}"
    return re.sub(pattern, new_value, current_condition)

class Result:
    def __init__(self, status: int, counter_example: str, path_constrain: str,dt: str = ""):
        self.status = status
        self.counter_example = counter_example
        self.path_constrain = path_constrain
        self.dt = dt
    def to_json(self) -> str:
        return json.dumps(self.__dict__)
    @classmethod
    def from_json(cls, json_string: str) -> 'Result':
        data_dict = json.loads(json_string)
        return cls(data_dict["status"], data_dict["counter_example"], data_dict["path_constrain"],data_dict["dt"])
    def __str__(self):
        return f"Result(status={self.status}, counter_example={self.counter_example}, path_constrain={self.path_constrain}), dt={self.dt})"

class SpecUnit:
    def __init__(self, program: str, T: str, D: str, pre_constrains: List[str], tmp_var_types:dict):
        self.program = program  # string 类型字段
        self.T = T
        self.D = D
        self.pre_constrains = pre_constrains

        #About About TMPVAR
        self.temp_var_types = tmp_var_types

    def to_json(self) -> str:
        """
        将 SpecUnit 对象序列化为 JSON 字符串
        """
        return json.dumps(self.__dict__)

    @classmethod
    def from_json(cls, json_string: str) -> 'SpecUnit':
        """
        从 JSON 字符串反序列化为 SpecUnit 对象
        """
        data_dict = json.loads(json_string)
        return cls(data_dict["program"], data_dict["T"], data_dict["D"],data_dict["pre_constrains"],data_dict["temp_var_types"])

    def __str__(self):
        return f"SpecUnit(name={self.program}, T={self.T}, D={self.D},pre_constrains={self.pre_constrains}, temp_var_types={self.temp_var_types})"

############# java_expr_z3_expr ##############
def solver_check_z3(z3_expr:str, vars_types:dict = "")->str:
    try:
        solver = Solver()
        solver.add(z3_expr)

        if solver.check() == sat:
            print("The expression is satisfiable.")
            model = solver.model()
            model_str = "["
            # 更完整的流程：遍历所有变量类型字典，主动用 model.eval 获取值
            for var_name, var_type in vars_types.items():
                if var_name == "return_value":
                    continue
                try:
                    z3_val = model.eval(z3.Bool(var_name) if var_type in ["bool", "boolean"] else z3.BitVec(var_name, 32) if var_type in ["int", "char"] else z3.Real(var_name), model_completion=True)
                    if var_type == "int":
                        var_value = str(z3_val.as_signed_long())
                    elif var_type == "char":
                        var_value = chr(z3_val.as_long() & 0x10FFFF)
                    elif var_type in ["bool", "boolean"]:
                        var_value = str(z3_val)
                    elif var_type == "double":
                        var_value = str(z3_val)
                    else:
                        var_value = str(z3_val)
                    model_str = model_str + f"{var_name}={var_value}, "
                except Exception as e:
                    model_str = model_str + f"{var_name}=ERROR, "
            model_str = model_str.rstrip(", ") + "]"
            print(model_str)
            return model_str
        else:
            print("The expression is unsatisfiable.")
            #创建 Result 对象
            return "OK"

    except Exception as e:
        print("solver check fail!")
        print("错误信息:", e)
        raise
        # return "ERROR"

def parse_result(z3_result_str:str)->dict:
    var_values_dict = {}
    z3_result_str = z3_result_str.strip("[").strip("]")
    var_values = z3_result_str.split(",")
    for var_value in var_values:
        tmp = var_value.split("=")
        var_values_dict[tmp[0].strip()] = tmp[1].strip()
    return var_values_dict

def replace_char_literals(expr):
    # 替换 Java 表达式中的字符字面量，如 'a' -> 97
    return re.sub(r"'(.)'", lambda m: str(ord(m.group(1))), expr)

def to_z3_val(val):
    if isinstance(val, int):
        return z3.IntVal(val)
    if isinstance(val, float):
        return z3.RealVal(val)
    return val

def java_expr_to_z3(expr_str, var_types: dict):
    """
    :param expr_str: Java格式逻辑表达式，如 "(b1 == true && x > 5)"
    :param var_types: dict，变量名到类型的映射，如 {'b1': 'bool', 'x': 'int'}
    :return: Z3 表达式
    """
    expr_str = expr_str.strip()
    expr_str = expr_str.lstrip()
    expr_str = " ".join(expr_str.splitlines())
    print(f"Java expression: {repr(expr_str)}")
    expr_str = remove_type_transfer_stmt_in_expr(expr_str)
    z3_vars = {}
    for name, vtype in var_types.items():
        if vtype == 'boolean' or vtype == 'bool':
            z3_vars[name] = z3.Bool(name)
        elif vtype == 'int':
            z3_vars[name] = z3.BitVec(name,32)
            # z3_vars[name] = z3.Int(name)
        elif vtype == 'char':
            z3_vars[name] = z3.BitVec(name,32)
            # z3_vars[name] = z3.Int(name)
        elif vtype == 'double':
            z3_vars[name] = z3.Real(name)
        elif vtype == "void":
            continue
        elif "List" in vtype or "Map" in vtype or "Set" in vtype or "String" in vtype or "Integer" in vtype \
                or "Float" in vtype or "Double" in vtype or "Boolean" in vtype or "Object" in vtype:
            continue
        else:
            raise ValueError(f"不支持的变量类型: {vtype}")

    expr_str = replace_char_literals(expr_str)
    expr_str = expr_str.replace("true", "True").replace("false", "False")
    expr_str = expr_str.replace("&&", " and ").replace("||", " or ").replace("!", " not ")
    expr_str = expr_str.replace("not =","!=")
    expr_str = expr_str.strip()

    class Z3Transformer(ast.NodeTransformer):
        def visit_Name(self, node):
            if node.id in z3_vars:
                return z3_vars[node.id]
            elif node.id in {"char","int", "boolean","float", "double"}: #avoid that the keywords in Java are recognized as variables
                return ""
            else:
                print_unknown_variable_result(f"Unknown variable : {node.id}")
                raise ValueError(f"unknown vars: {node.id}")

        def visit_Constant(self, node):
            if isinstance(node.value, bool):
                return node.value
            elif isinstance(node.value, int):
                return z3.BitVecVal(node.value,32)
                # return z3.IntVal(node.value)
            elif isinstance(node.value, float):
                return z3.RealVal(node.value)
            elif isinstance(node.value, str):
                if len(node.value) == 1:
                    return z3.BitVecVal(ord(node.value),32)
                    # return z3.IntVal(node.value)
                return node.value
            else:
                raise ValueError(f"unacceptable type: {node.value}")

        def visit_BoolOp(self, node):
            values = [self.visit(v) for v in node.values]
            if isinstance(node.op, ast.And):
                return z3.And(*values)
            elif isinstance(node.op, ast.Or):
                return z3.Or(*values)
            else:
                raise ValueError(f"unacceptable operation of bool: {type(node.op)}")

        def visit_UnaryOp(self, node):
            if isinstance(node.op, ast.Not):
                return z3.Not(self.visit(node.operand))
            if isinstance(node.op, ast.USub):
                return -self.visit(node.operand)
            else:
                raise ValueError(f"unacceptable operation of UnaryOp: {type(node.op)}")

        def visit_Compare(self, node):
            left = self.visit(node.left)
            right = self.visit(node.comparators[0])
            op = node.ops[0]


            left = to_z3_val(left)
            right = to_z3_val(right)
            #
            # Int/Real -> Real
            if (z3.is_int_value(left) and z3.is_real(right)) or (z3.is_real(left) and z3.is_int_value(right)):
                left = z3.ToReal(left)
                right = z3.ToReal(right)

            # BitVec mix with Int -> Int
            if isinstance(left, z3.BitVecRef) and isinstance(right, z3.IntNumRef):
                left = z3.BV2Int(left, is_signed=False)
            if isinstance(right, z3.BitVecRef) and isinstance(left, z3.IntNumRef):
                right = z3.BV2Int(right, is_signed=False)
            # # make sure BitVec used in the same size
            if is_bv(left) and is_bv(right):
                if left.size() == 16 and right.size() == 32:
                    left = SignExt(16, left)
                if right.size() == 16 and left.size() == 32:
                    right = SignExt(16, right)
            if isinstance(op, ast.Eq):
                return left == right
            elif isinstance(op, ast.NotEq):
                return left != right
            elif isinstance(op, ast.Gt):
                return left > right
            elif isinstance(op, ast.GtE):
                return left >= right
            elif isinstance(op, ast.Lt):
                return left < right
            elif isinstance(op, ast.LtE):
                return left <= right
            else:
                raise ValueError(f"不支持的比较运算符: {type(op)}")

        def visit_BinOp(self, node):
            left = self.visit(node.left)
            right = self.visit(node.right)
            op = node.op

            if isinstance(op, ast.Add):
                return left + right
            elif isinstance(op, ast.Sub):
                return left - right
            elif isinstance(op, ast.Mult):
                return left * right
            elif isinstance(op, ast.Div):
                return left / right
            elif isinstance(op, ast.Mod):
                # return left % right
                return z3.SRem(left, right)
            elif isinstance(op, ast.Pow):
                # 支持幂运算 x ** n
                if isinstance(left, z3.BitVecRef) and isinstance(right, z3.BitVecRef):
                    left1 = BV2Int(left, is_signed=True)
                    right1 = BV2Int(right, is_signed=True)
                return Int2BV(left1 ** right1,32)
            elif isinstance(op, ast.BitAnd):
                # 确保操作数都是位向量
                if not (isinstance(left, z3.BitVecRef) and isinstance(right, z3.BitVecRef)):
                    left = z3.Int2BV(left,32) if is_int(left) else left
                    right = z3.Int2BV(right,32) if is_int(right) else right
                return left & right
            elif isinstance(op, ast.BitOr):
                if not (isinstance(left, z3.BitVecRef) and isinstance(right, z3.BitVecRef)):
                    left = z3.Int2BV(left,32) if is_int(left) else left
                    right = z3.Int2BV(right,32) if is_int(right) else right
                    # raise TypeError("按位或运算的操作数必须是位向量")
                return left | right
            elif isinstance(op, ast.BitXor):
                if not (isinstance(left, z3.BitVecRef) and isinstance(right, z3.BitVecRef)):
                    left = z3.Int2BV(left,32) if is_int(left) else left
                    right = z3.Int2BV(right,32) if is_int(right) else right
                    # raise TypeError("按位异或运算的操作数必须是位向量")
                return left ^ right
            else:
                raise ValueError(f"不支持的算术操作: {type(op)}")
    try:
        parsed = ast.parse(expr_str, mode="eval")
    except Exception as e:
        print(f"ast.parse error: {e}, expr_str={repr(expr_str)}")
        z3_expr = f"ERROR Info: {e}"
        raise
    try:
        z3_expr = Z3Transformer().visit(parsed.body)
    except Exception as e:
        print(f"Z3Transformer 处理异常: {e}")
        z3_expr = f"ERROR Info: {e}"
        raise
    return z3_expr

def parse_md_def(java_code: str) -> dict:
    lines = java_code.splitlines()
    var_types = {}
    for line in lines:
        line = line.strip()
        if line.startswith("public static") and "main" not in line:
            return_type = line.split()[2]
            params_def = line.split("(")[1].split(")")[0]
            var_types["return_value"] = return_type
            if params_def.strip():
                params = params_def.split(",")
                for param in params:
                    param = param.strip()
                    param_type = param.split()[0]
                    param_name = param.split()[1]
                    var_types[param_name] = param_type
            print(var_types)
    return var_types


def parse_class_name(java_code: str) -> str:
    m = re.search(r'^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?'
                  r'class\s+([A-Za-z_][A-Za-z0-9_]*)', java_code, re.MULTILINE)
    return m.group(1) if m else "classNameUnknown"


############# java_expr_z3_expr ##############

def add_value_constraints(logic_expr: str, var_types: dict) -> str:
    """
    添加变量值约束到逻辑表达式中
    :param logic_expr: 原始逻辑表达式
    :param var_types: 变量类型字典
    :return: 添加了变量值约束的逻辑表达式
    """
    value_constraints_expr = ""
    for var, vtype in var_types.items():
        if var == "return_value":
            continue
        if vtype == 'int':
            value_constraints_expr += f" && ({var} >= -32768 && {var} <= 32767)"
        elif vtype == 'char':
            value_constraints_expr += f" && ({var} >= 32 && {var} <= 126)"
    value_constraints_expr = value_constraints_expr.strip().strip("&&").strip()
    if len(value_constraints_expr) > 0:
        logic_expr = f"({logic_expr})" + f" && ({value_constraints_expr})"
    return logic_expr

def existFlagInPath(executionPath:str):
    for step in executionPath:
        if step.startswith("REP"):
            return True
    return False

def print_spec_unit(spec_unit: SpecUnit):
    print("SpecUnit Details:")
    print(f"Program: {spec_unit.program}")
    print(f"T: {spec_unit.T}")
    print(f"D: {spec_unit.D}")
    print(f"Pre-constrains: {spec_unit.pre_constrains}")

def deal_with_spec_unit_json(spec_unit_json: str):
    spec_unit = None
    try:
        spec_unit = SpecUnit.from_json(spec_unit_json)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON: {e}")
        return

    print("z3_runner get the su json successfully.")
    program = spec_unit.program
    T = spec_unit.T
    D = spec_unit.D
    previous_cts = spec_unit.pre_constrains

    print_spec_unit(spec_unit)

    try:
        output = run_java_code(program, timeout_seconds=20)
    except subprocess.TimeoutExpired:
        print("run_java_code")
        print_verification_timeout_result()
        return

    execution_output = ""
    if output is None:
        print("Java code execution failed.")
        return

    if output.stderr is not None and "Exception" in output.stderr:
        result = Result(-2,"Exception founded:" + str(output.stderr),"")
        print("result:" + result.to_json())
        return

    if output.stdout is not None:
        execution_output = output.stdout
    if not execution_output:
        print("No output from Java code execution.")
    var_types = parse_md_def(program)

    input_vars = list(var_types.keys())
    execution_path = parse_execution_path(execution_output)
    print("\n******Start Execution Path******")
    for step in execution_path:
        print(step)
    print("******End Execution Path******")

    # get the CSC path first
    # then build the tree

    csc_path = execution_path_2_csc_path(execution_path,input_vars)
    print(f"csc_path:{csc_path}")
    class_name = parse_class_name(program)
    tmp_file_path = f"csc_tmp/{class_name}.txt"

    cct = CCT()
    if os.path.exists(tmp_file_path):
        cct = CCT.load_from_file(tmp_file_path)
    condition_results_sequence = parse_path_info(csc_path)
    print(f"condition_results_sequence: {condition_results_sequence}")
    cct.add_sequence(condition_results_sequence,"checked")
    cct.print_tree()
    cct.save_to_file(tmp_file_path)

    isSafePath = not existFlagInPath(execution_path)
    if isSafePath:
        print("***[SAFE PATH]***\nNo TBFVPS_FLAG found in the path, the path is a safe path!")


    current_ct = get_ct_from_execution_path(execution_path)

    print(f"**original current_ct**: {current_ct}")


    if current_ct == "":
        current_ct = "true"
    solver_result = ""

    new_d = ""
    ## if the testcase do not run through the dangerous path, there is no need to verify.
    if not isSafePath:
        print(f"current_Ct_: {current_ct}")
        print(f"original D : {D}")
        new_d = update_D_with_execution_path(D,execution_path,input_vars)
        print("new_d:" + new_d)
        negated_d = f"!({new_d})"
        new_logic_expression = f"({T}) && ({current_ct}) && ({negated_d})"
        new_logic_expression = add_value_constraints(new_logic_expression, var_types)
        print(f"\nT && Ct && !D: {new_logic_expression}")
        z3_expr = java_expr_to_z3(new_logic_expression, var_types)
        print("T && Ct && !D as z3 expression: " + str(z3_expr))
        solver_result = solver_check_z3(z3_expr,var_types)

    previous_cts.append(current_ct)
    hasOtherPath = hasOtherPaths(T, previous_cts, var_types)

    if new_d == "":
        new_d = "true"
    if  solver_result == "" or solver_result == "OK":
        if hasOtherPath:
            result = Result(0,"",current_ct,new_d)
        else:
            result = Result(3,"",current_ct,new_d)
    else:
        print("check point: found error path")
        result_str = parse_result(solver_result)
        tcs = keep_throwing_tcs_until_no_more(current_ct,new_d,var_types,result_str,0)
        tcs_str = ";".join(tcs)
        if hasOtherPath:
            result = Result(2,tcs_str,current_ct,new_d)
        else:
            result = Result(4,tcs_str,current_ct,new_d)
    print("result:" + result.to_json())

def hasOtherPaths(T:str, previous_cts:List[str], var_types:dict)->bool:
    combined_expr = combind_expr_and_list(f"({T})", previous_cts)
    combined_expr = add_value_constraints(combined_expr, var_types)
    print("hasOtherPaths check point, the current (T) && !(previous_cts) && !(current_ct): " + combined_expr)
    z3_expr = java_expr_to_z3(combined_expr, var_types)
    # print("(T) && !(previous_cts) && !(current_ct) as z3 expression: " + str(z3_expr))
    currntPathsCheck = solver_check_z3(z3_expr,var_types)
    if(currntPathsCheck == "OK"):
        return False
    else:
        return True

def remove_type_transfer_stmt_in_expr(expr: str) -> str:
    ans = expr.replace("(long)","").replace("(int)","").replace("(short)","").replace("(byte)","").replace("(char)","")
    return ans

def get_ct_from_execution_path(execution_path:List[str]):
    ct = ""
    for step in reversed (execution_path):
        if "Evaluating if condition" in step:
            condition_match = re.search(r"Evaluating if condition: (.*?) is evaluated as: (.*?)", step)
            if condition_match:
                if_condition = condition_match.group(1).strip()
                if_condition = remove_type_transfer_stmt_in_expr(if_condition)
                ct = f"{ct} && {if_condition}"
            # Check whether it is a condition to enter the loop
        elif "Entering loop" in step:
            condition_match = re.search(r"Entering loop with condition: (.*?) is evaluated as: true", step)
            if condition_match:
                loop_condition = condition_match.group(1).strip()
                ct = f"{ct} && {loop_condition}"
        elif "Entering forloop" in step:
            condition_match = re.search(r"Entering forloop with condition: (.*?) is evaluated as: true", step)
            if condition_match:
                loop_condition = condition_match.group(1).strip()
                ct = f"{ct} && {loop_condition}"

            # Check whether it is a condition for exiting the loop
        elif "Exiting loop" in step:
            condition_match = re.search(r"Exiting loop, condition no longer holds: (.*?) is evaluated as: false", step)
            if condition_match:
                loop_condition = condition_match.group(1).strip()
                ct = f"{ct} && !{loop_condition}"
        elif "Exiting forloop" in step:
            condition_match = re.search(r"Exiting forloop, condition no longer holds: (.*?) is evaluated as: false", step)
            if condition_match:
                loop_condition = condition_match.group(1).strip()
                ct = f"{ct} && !{loop_condition}"

            # Check for variable assignment
        elif "current value" in step:
            assignment_match = re.search(r"(.*?) = (.*?), current value of (.*?): (.*?)$", step)
            if assignment_match:
                variable = assignment_match.group(1).strip()
                value = assignment_match.group(2).strip()
                ct = replace_variables(ct,variable,value)
        elif "Under condition" in step:
            condition_assignment_match = re.search(r"Under condition (.*) = (.*), condition is : (.*)", step)
            if condition_assignment_match:
                variable = condition_assignment_match.group(1).strip()
                value = condition_assignment_match.group(2).strip()
                ct = replace_variables(ct,variable,value)
        elif "NP detecting: " in step:
            condition_assignment_match = re.search(r"Np detecting: (.*) = (.*)", step)
            if condition_assignment_match:
                variable = condition_assignment_match.group(1).strip()
                value = condition_assignment_match.group(2).strip()
                ct = replace_variables(ct,variable,value)

    return ct.strip().strip("&&")

def update_D_with_execution_path(D: str, execution_path: List[str], input_vars: List[str]) -> str:
    if("return_value" in D):
        D = replace_variables(D,"return_value","(return_value)")
    D = D.replace("(char)", "").replace("(long)","").replace("(int)","").replace("(double)","")
    newd = D
    for step in reversed(execution_path):
        if "current value" in step or "Function input" in step or "Under condition" in step or "NP detecting: " in step:
            assignment_match = re.search(r"(.*?) = (.*?), current value of (.*?): (.*?)$", step)
            condition_assignment_match = re.search(r"Under condition (.*) = (.*), condition is : (.*)", step)
            NP_match = re.search(r"NP detecting: (.*) = (.*)", step)
            type = ""
            if assignment_match:
                variable = assignment_match.group(1).strip()
                value = assignment_match.group(2).strip()
            elif condition_assignment_match:
                variable = condition_assignment_match.group(1).strip()
                value = condition_assignment_match.group(2).strip()
            elif NP_match:
                variable = NP_match.group(1).strip()
                value = NP_match.group(2).strip()
            else :
                continue

            if type and type == "char":
                value = f"'{value}'"
            newd = replace_variables(newd,variable,value)

    for input_var in input_vars:
        if f"__{input_var}__" in newd:
            newd = replace_variables(newd, f"__{input_var}__", input_var)
    return newd.strip().strip("&&")

def read_java_code_from_file(file_path):
    """
    Read Java code from the specified file.
    """
    with open(file_path, "r") as file:
        java_code = file.read()
    return java_code

def z3_generate_testcase(spec_unit_json:str):
    spec_unit = None
    r = Result(0,"","")
    # print(f"Processing SpecUnit JSON: {spec_unit_json}")
    try:
        spec_unit = SpecUnit.from_json(spec_unit_json)
    except json.JSONDecodeError as e:
        print(f"{UNHANDLED_ERROR}: z3_solver_runner failed to resolve the spec_unit {e}")
        return
    print("z3_runner get the gu json successfully.")
    constrains_expr = spec_unit.T
    program = spec_unit.program
    var_types = parse_md_def(program)
    z3_expr = java_expr_to_z3(constrains_expr, var_types)
    print(f"z3 generating testcase under constrains: [{z3_expr}]")
    var_values = solver_check_z3(z3_expr,var_types)
    if(var_values == "OK"):
        r = Result(1,"","")
    else:
        r = Result(0,var_values,"")
    print(f"{TESTCASE_GENERATION_RESULT}: {r.to_json()}")

def run_with_timeout(func, arg, timeout_seconds, task_name):
    with concurrent.futures.ThreadPoolExecutor() as executor:
        future = executor.submit(func, arg)
        try:
            future.result(timeout=timeout_seconds)
        except concurrent.futures.TimeoutError:
           print_verification_timeout_result()
        except Exception as e:
            print_verification_unexpected_result()

#当发现某个path是不安全的时，每每得到一个导致error的解，就扔掉它（x != n）(x != n && y != m)然后继续进行一次求解
#递归地收集所有tc，从最后一个收起
def keep_throwing_tcs_until_no_more(current_ct:str, new_d:str,var_types:dict, var_values:dict,cur_num:int)->List[str]:
    if cur_num > TBFV_PS_MAX_NUM_IN_ONE_PATH:
        return [f"{TBFV_PS_TOO_MANY_ERROR_FLAG}==1"]
    tcs = []
    tc = ""
    for var, val in var_values.items():
        if var in var_types.keys():
            tc = f"{tc} && {var} == {val}"
    tc = tc.strip().strip("&&").strip()
    tcs.append(tc)
    T_and_Ct_minus_tc = f"({current_ct}) && !({tc})"
    newT_and_Ct_and_not_D = f"({T_and_Ct_minus_tc}) && !({new_d})"
    newT_and_Ct_and_not_D = add_value_constraints(newT_and_Ct_and_not_D, var_types)
    z3_expr = java_expr_to_z3(newT_and_Ct_and_not_D, var_types)
    print(f"keep throwing tcs, new T && Ct : {T_and_Ct_minus_tc}")
    solver_result = solver_check_z3(z3_expr,var_types)
    #OK means unsat
    if solver_result == "OK":
        return tcs
    else:
        new_var_values = parse_result(solver_result)
        tmp_tcs = keep_throwing_tcs_until_no_more(T_and_Ct_minus_tc, new_d,var_types,new_var_values,cur_num+1)
        tcs.extend(tmp_tcs)
    return tcs

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--su', '--specUnit', help='输入要验证的SpecUnit对象的JSON字符串', required=False)
    parser.add_argument('-g', '--gu', '--generationUnit', help='输入带有约束条件和程序的generationUnit', required=False)
    parser.add_argument('-c', '--csc', '--cscUnit', help='generating tcs by csc ', required=False)
    args = parser.parse_args()
    spec_unit_json = args.su
    generation_unit = args.gu
    csc_unit = args.csc
    if spec_unit_json is None and generation_unit is None and csc_unit is None:
        print("No verification task specified. Use -s for SpecUnit verification or -g for testcase generation.")
        return
    if spec_unit_json is not None:
        run_with_timeout(deal_with_spec_unit_json, spec_unit_json, 20, "SpecUnit verify")

    if generation_unit is not None:
        print(f"Here in z3_runner the gu is: {generation_unit}")
        run_with_timeout(z3_generate_testcase, generation_unit, 20, "testcase generation")
    if csc_unit is not None:
        generate_tcs_by_csc(csc_unit)


def test_main():
    gu = "{\"program\":\"import java.util.Scanner;\n\npublic class Test02 {\n\n    public static void divBy0(int x, int y) {\n        int z = 0;\n        if (x > 0) {\n            y = x * 2;\n        } else {\n            y = x * 3;\n            x = 10;\n        }\n        x++;\n        z = x / y;\n    }\n}\n\",\"T\":\"true && ( x <= 32767 ) && ( x >= -32768 ) && ( y <= 32767 ) && ( y >= -32768 )\",\"D\":\"true\",\"pre_constrains\":[]}"
    gu = gu.replace("\n", "\\n")
    print(gu)
    run_with_timeout(z3_generate_testcase, gu, 20, "testcase generation")

def test_main2():
    su = ""
    su = su.replace("\n", "\\n")
    run_with_timeout(deal_with_spec_unit_json, su, 20, "SpecUnit verify")

def test_parse_result():
    s = "[x = 1, y = 2]"
    dict = parse_result(s)
    for key in dict.keys():
        print(f"{key} = {dict[key]}")

def test_keep_throwing_tcs_until_no_more():
    v_t = {"y":"int"}
    v_v = {"y":"0"}
    current_ct = "(y < 10000) && (y > -10000)"
    D = "(y !=0 )"
    tcs = keep_throwing_tcs_until_no_more(current_ct,D,v_t,v_v,0)
    solver_result = ";".join(tcs)
    print("solver_result:" + solver_result)
def test_random():
    java_expr = "'((true) && ( (n > 0) && !(n < 0)) && (!(true))) && ((n >= -32768 && n <= 32767))'"
    v_t = {"n":"int"}
    z3_expr = java_expr_to_z3(java_expr, v_t)
    print(f"{z3_expr}")

# ================== Here are the code for CSC ==================

# --------- For CSC ---------

#1. 先写一个忽略行号的版本，这意味着我们先不需要改PathPrinter，同时要求，所有测试程序中不要出现一摸一样的condition expr
#2. 妥善地把 execution path 转成 csc 能用的，例如 :
#           Evaluating if condition: !(adjusted > 40) is evaluated as: true
#           必须转为： adjusted > 40, false
#3. 然后把转换后的 csc path 进行分析，主要做三件事，一个是再遍一遍把condition出现的次数统计出来，如 adjust > 40, false,1
# 另一个是，求出每个condition的低层约束式，注意 adjust > 40, false,1 和  adjust > 40, false,2 的约束式肯定不一样
# 最后封装成 condition 对象，交给CCT处理

# 获得只有condition的路径，形如 con_expr,true,low_level_con,1


def generate_tcs_by_csc(csc_unit:str):
    try:
        spec_unit = SpecUnit.from_json(csc_unit)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON: {e}")
        return

    print("csc checker get the csc_unit json successfully.")
    program = spec_unit.program
    class_name = parse_class_name(program)
    var_types = parse_md_def(program)
    tmp_file_path = f"csc_tmp/{class_name}.txt"
    cct = CCT()
    if os.path.exists(tmp_file_path):
        cct = CCT.load_from_file(tmp_file_path)
    cct.print_tree()
    r = Result(1,"","")
    condition_results = cct.check_for_csc()
    print("condition_results found by csc!")
    # for con in condition_results:
    #     print(f"{con}")
    if condition_results:
        new_path = cct.construct_path_constraint(condition_results)
        print(f"new_path is {new_path}")
        print(f"csc find the empty path: {condition_results},let's generate testcase for it")
        new_path = add_value_constraints(new_path,var_types)
        z3_expr = java_expr_to_z3(new_path,var_types)
        solver_result = solver_check_z3(z3_expr,var_types)
        #OK means UNSAT
        if(solver_result == "OK"):
            cct.mark_infeasible(condition_results)
            print("just after mark_infeasible")
            r = Result(1,"","")
            print(f"{CSC_CHECKING_RESULT}: {r.to_json()}")
        else:
            r = Result(0,solver_result,"")
            print(f"{CSC_CHECKING_RESULT}: {r.to_json()}")
    else:
        r = Result(5,"","")
        print(f"{CSC_CHECKING_RESULT}: {r.to_json()}")
    cct.save_to_file(tmp_file_path)

def execution_path_2_csc_path(execution_path:List[str],vars:List[str])->List[str]:
    csc_path = []
    for i in range(len(execution_path)):
        step = execution_path[i]
        print("dealing with step:" + step)
        if contains_condition_expr(step):
            condition = trans_2_csc_condition(step)
            print(f"condition1 here is {condition}")
            onlycondition = condition.split(",")[0]
            pre_execution_path = execution_path[:i]
            low_level_con = update_D_with_execution_path(onlycondition,pre_execution_path,vars)
            split_condition = condition.split(",")[0].strip()
            split_condition = split_condition.strip("()")
            split_status = condition.split(",")[1]
            condition = f"{split_condition},{split_status}"
            condition = f"{condition},{low_level_con}"
            print("condition:" + condition)
            csc_path.append(condition)
    #加上loop_count, 这样condition 序列形如： con_expr,low_level,true,1
    csc_path_with_loop_count = []
    map:dict[str,int] = {}
    for condition in csc_path:
        only_condition = condition.split(",")[0]
        if not only_condition in map:
            map[only_condition] = 1
            csc_path_with_loop_count.append(f"{condition},1")
        else:
            map[only_condition] = map[only_condition] + 1
            csc_path_with_loop_count.append(f"{condition},{map[only_condition]}")
    return csc_path_with_loop_count

def contains_condition_expr(step:str)->bool:
    if "Evaluating if condition" in step or "Entering loop" in step or "Exiting loop" in step or \
            "Entering forloop" in step or "Exiting forloop" in step or "Under condition" in step:
        return True
    return False

def trans_2_csc_condition(step:str)->str:
    condition = ""
    if "Evaluating if condition" in step:
        condition_match = re.search(r"Evaluating if condition: (.*?) is evaluated as: (.*?)", step)
        if condition_match:
            condition = condition_match.group(1).strip()
            condition = remove_type_transfer_stmt_in_expr(condition)
            if(condition.startswith("!(")) and condition.endswith(")"):
                condition = condition.removeprefix("!(")
                condition = condition.removesuffix(")")
                if condition.startswith('(') and condition.endswith(')'):
                    condition = condition[1:-1]
                condition = f"{condition},False"
            else:
                condition = f"{condition},True"
    elif "Entering loop" in step:
        condition_match = re.search(r"Entering loop with condition: (.*?) is evaluated as: true", step)
        if condition_match:
            condition = condition_match.group(1).strip()
            if condition.startswith('(') and condition.endswith(')'):
                condition = condition[1:-1]
            condition = f"{condition},True"
    elif "Entering forloop" in step:
        condition_match = re.search(r"Entering forloop with condition: (.*?) is evaluated as: true", step)
        if condition_match:
            condition = condition_match.group(1).strip()
            if condition.startswith('(') and condition.endswith(')'):
                condition = condition[1:-1]
            condition = f"{condition},True"
    elif "Exiting loop" in step:
        condition_match = re.search(r"Exiting loop, condition no longer holds: (.*?) is evaluated as: false", step)
        if condition_match:
            condition = condition_match.group(1).strip()
            if condition.startswith('(') and condition.endswith(')'):
                condition = condition[1:-1]
            condition = f"{condition},False"
    elif "Exiting forloop" in step:
        condition_match = re.search(r"Exiting forloop, condition no longer holds: (.*?) is evaluated as: false", step)
        if condition_match:
            condition = condition_match.group(1).strip()
            if condition.startswith('(') and condition.endswith(')'):
                condition = condition[1:-1]
            condition = f"{condition},False"
    return condition


# --------------------------

# Marker for infeasible paths, corresponding to the 'X' symbol in the paper
INFEASIBLE_MARKER = "X"

# ----------------------------------------------------------------------
# 1. Helper Data Structures (Condition and Condition Result)
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class Condition:
    """
    Represents a condition (l, c, k) and its input constraint derived from WP analysis.

    line_number (l): Line number.
    condition_string (c): The original string representation of the condition (e.g., 'x >= 2').
    input_constraint: The constraint expression on the initial input parameters (I), calculated via WP.
    loop_count (k): The k-th time this condition is executed in the current path.
    """
    line_number: int
    condition_string: str
    input_constraint: str  # Field to store the WP-derived constraint on initial inputs
    loop_count: int = 1

    # Custom __eq__ and __hash__ based only on condition_string and loop_count for CCT traversal
    def __eq__(self, other):
        if not isinstance(other, Condition):
            return NotImplemented
        # Only compare based on condition string (c) and loop count (k)
        return (self.condition_string == other.condition_string and
                self.loop_count == other.loop_count)

    def __hash__(self):
        return hash((self.condition_string, self.loop_count))

@dataclass(frozen=True)
class ConditionResult:
    """
    Represents a condition and its evaluation result ( ((l, c, k), T/F) )

    condition: The Condition object.
    result: The evaluation result (True for T, False for F).
    """
    condition: Condition
    result: bool

# ----------------------------------------------------------------------
# 2. Path Information Parsing Tool
# ----------------------------------------------------------------------

def parse_path_info(path_info_list: List[str]) -> List['ConditionResult']:
    """
    Converts a list of specially formatted path information strings into a list of ConditionResult objects.

    Expected format: 'condition_str,result_bool,input_constraint,loop_count'

    Example: '(x >= 2),True,x >= 2 && y == 0,1'

    :param path_info_list: List of strings to parse.
    :return: List of ConditionResult objects.
    """
    results = []
    for info_str in path_info_list:
        try:
            # 1. Split the string
            parts = [part.strip() for part in info_str.split(',')]

            # Must contain 4 required parts: condition string, result boolean, WP constraint, loop count
            if len(parts) < 4:
                print(f"Warning: Skipping malformed line (less than 4 parts): {info_str}")
                continue

            # Expected format: cond, result, WP_constraint, count
            condition_string = parts[0].strip('()')
            result_bool_str = parts[1].strip()
            input_constraint = parts[2].strip() # Extract the WP-derived constraint
            loop_count_str = parts[3].strip()

            # 2. Convert data types
            result_bool = result_bool_str.lower() == 'true'
            loop_count = int(loop_count_str)

            # 3. Create Condition and ConditionResult
            cond = Condition(
                line_number=1, # Placeholder line number
                condition_string=condition_string,
                input_constraint=input_constraint,
                loop_count=loop_count
            )
            cr = ConditionResult(condition=cond, result=result_bool)
            results.append(cr)
        except Exception as e:
            # Print error but don't break the entire parsing process
            print(f"Error parsing line '{info_str}': {e}")
            continue

    return results

# ----------------------------------------------------------------------
# 3. CCT Tree Node Class
# ----------------------------------------------------------------------

class Node:
    """
    An internal node of the CCT (Condition Case Tree).
    It can be an internal node (storing a Condition) or a leaf node (storing a set of test cases or the infeasible marker).
    """
    def __init__(self, data: Any, is_leaf: bool):
        self.is_leaf: bool = is_leaf
        self.left: Optional['Node'] = None  # F (False) branch
        self.right: Optional['Node'] = None # T (True) branch

        if is_leaf:
            # Leaf node stores the set of Test Cases or INFEASIBLE_MARKER
            self.test_cases: Set[str] = {data}
            self.condition: Optional[Condition] = None
        else:
            # Internal node stores the Condition
            self.condition: Condition = data
            self.test_cases: Optional[Set[str]] = None

    def add_test_case(self, test_case: str):
        """Helper method to add a test case to an existing leaf node."""
        if self.is_leaf and self.test_cases is not None:
            # Ensure it's not a marked infeasible leaf
            if INFEASIBLE_MARKER not in self.test_cases:
                self.test_cases.add(test_case)

    def __repr__(self):
        """For debugging and printing."""
        if self.is_leaf:
            if self.test_cases == {INFEASIBLE_MARKER}:
                return "Leaf(Infeasible=X)"
            sorted_cases = sorted(list(self.test_cases))
            return f"Leaf(Cases={sorted_cases})"
        else:
            # Display Condition, line number, loop count, and WP constraint
            return (f"Node(Cond='{self.condition.condition_string}' @L{self.condition.line_number} "
                    f"(Cnt={self.condition.loop_count}), WP='{self.condition.input_constraint}')")

# ----------------------------------------------------------------------
# 4. CCT Main Class (Core Algorithms: Append, Mark Infeasible, Check for CSC)
# ----------------------------------------------------------------------

class CCT:
    """
    Condition Case Tree (CCT) main class, used to maintain the tree structure.
    """
    MAX_LOOP_BOUND = 2

    def __init__(self):
        """Initializes an empty CCT."""
        self.root: Optional[Node] = None
    # --- Persistence Methods ---

    def save_to_file(self, filepath: str):
        """
        Persists the CCT structure to a file.
        Uses pickle to serialize the root node, as it handles complex Python object graphs.

        :param filepath: The path to the file to save to.
        """
        if self.root is None:
            print("Warning: Root node is empty, no CCT to save.")
            return
        try:
            # 'wb' mode for writing binary file
            with open(filepath, 'wb') as f:
                pickle.dump(self.root, f)
            print(f"Successfully saved CCT structure to file: {filepath}")
        except Exception as e:
            print(f"An error occurred while saving the CCT: {e}")

    @staticmethod
    def load_from_file(filepath: str) -> Optional['CCT']:
        """
        Reads content from a file and reconstructs the CCT instance.

        :param filepath: The path to the file to load.
        :return: The reconstructed CCT instance, or None if loading fails.
        """
        try:
            # 'rb' mode for reading binary file
            with open(filepath, 'rb') as f:
                root_node = pickle.load(f)

            # Create a new CCT instance and assign the loaded root
            cct = CCT()
            cct.root = root_node
            print(f"Successfully reconstructed CCT structure from file {filepath}.")
            return cct
        except FileNotFoundError:
            print(f"Error: File not found: {filepath}")
            return None
        except Exception as e:
            print(f"An error occurred while loading the CCT: {e}")
            return None
    def add_sequence(self, sequence: List[ConditionResult], test_case: str):
        """
        Appends a node sequence to the CCT (Algorithm 1 in the paper).
        """
        if not sequence:
            return

        # 1. Path Truncation Logic
        truncation_index = len(sequence)
        for i, cr in enumerate(sequence):
            if cr.condition.loop_count > CCT.MAX_LOOP_BOUND:
                truncation_index = i
                print(f"Path Truncation: Sequence for '{test_case}' stopped at step {i}. Loop count {cr.condition.loop_count} exceeds bound {CCT.MAX_LOOP_BOUND}. The remaining path is ignored.")
                break

        effective_sequence = sequence[:truncation_index]

        if not effective_sequence:
            print(f"Path Aborted: The initial condition of '{test_case}' already exceeds the loop bound.")
            return

        # Initialize root if empty
        if self.root is None:
            self.root = Node(effective_sequence[0].condition, is_leaf=False)

        current = self.root
        seq_len = len(effective_sequence)

        # Traverse and create internal nodes
        for i in range(seq_len - 1):
            current_result = effective_sequence[i]
            next_condition = effective_sequence[i + 1].condition

            # Structure check
            if current.is_leaf or current.condition != current_result.condition:
                print(f"Error: Sequence mismatch at step {i+1} with {current_result.condition}")
                return

            if not current_result.result: # F (False) branch -> left
                if current.left is None:
                    current.left = Node(next_condition, is_leaf=False)
                current = current.left
            else: # T (True) branch -> right
                if current.right is None:
                    current.right = Node(next_condition, is_leaf=False)
                current = current.right

        # Handle the last condition and create the leaf node
        last_result = effective_sequence[-1]

        if current.is_leaf or current.condition != last_result.condition:
            print(f"Error: Final sequence mismatch with {last_result.condition}")
            return

        if not last_result.result: # F (False) branch
            if current.left is None:
                current.left = Node(test_case, is_leaf=True)
            elif current.left.is_leaf:
                current.left.add_test_case(test_case)
            else:
                print("Error: Expected leaf node but found internal node in F branch.")
        else: # T (True) branch
            if current.right is None:
                current.right = Node(test_case, is_leaf=True)
            elif current.right.is_leaf:
                current.right.add_test_case(test_case)
            else:
                print("Error: Expected leaf node but found internal node in T branch.")

    def mark_infeasible(self, sequence: List[ConditionResult]):
        for con in sequence:
            print(f"sequence in mark_infeasible is {con}")
        """
        Marks a target condition sequence (P_target) as Infeasible ('X').
        """
        if not sequence:
            return

        current = self.root

        # Check if root is initialized
        if current is None:
            print("Error: Cannot mark infeasible in an empty CCT.")
            return

        # Traverse to the target internal node
        for cr in sequence[:-1]:
            if not cr.result: # F (False) -> left
                current = current.left
            else: # T (True) -> right
                current = current.right

            if current is None:
                print("Error: Cannot mark infeasible. Path structure lost midway.")
                return

        # Check if the current node is the parent of the leaf (must be an internal node)
        if current.is_leaf:
            print("Error: Infeasible mark failed. Target node is not an internal node (parent of the leaf).")
            return

        # Mark the target branch as infeasible ('X')
        last_result = sequence[-1]

        if not last_result.result: # F (False) branch
            current.left = Node(INFEASIBLE_MARKER, is_leaf=True)
            print(f"Successfully marked F branch after {current.condition} as Infeasible (X).")
        else: # T (True) branch
            current.right = Node(INFEASIBLE_MARKER, is_leaf=True)
            print(f"Successfully marked T branch after {current.condition} as Infeasible (X).")

    def _is_infeasible_leaf(self, node: Optional[Node]) -> bool:
        """Checks if a node is an infeasible leaf ('X')."""
        return node is not None and node.is_leaf and node.test_cases == {INFEASIBLE_MARKER}


    def check_for_csc(self) -> Optional[List[ConditionResult]]:
        """
        Checks for CSC (Condition Sequence Coverage) - Algorithm 2 in the paper.

        Finds the first (left-to-right, depth-first) uncovered branch ({Ø}), skipping infeasible paths (X).

        Returns:
          - List[ConditionResult]: The target condition sequence P_target (if found).
          - None: If all branches are covered or marked infeasible.
        """
        if self.root is None:
            return None

        target_path = self._check_recursive(self.root, [])
        return target_path

    def _check_recursive(self, node: Optional[Node], current_sequence: List[ConditionResult]) -> Optional[List[ConditionResult]]:
        """
        Recursive implementation for Algorithm 2.
        """
        if node is None:
            return None

        # --- F (False) Branch Check ---

        if node.left is None:
            # Found the {Ø} node (uncovered branch)
            target = current_sequence + [ConditionResult(node.condition, False)]
            return target

        # Skip covered leaf nodes and infeasible leaf nodes (X)
        if not self._is_infeasible_leaf(node.left) and not node.left.is_leaf:
            # If the left child is an internal node, continue recursion
            new_sequence = current_sequence + [ConditionResult(node.condition, False)]
            result = self._check_recursive(node.left, new_sequence)
            if result is not None:
                return result

        # --- T (True) Branch Check ---

        if node.right is None:
            # Found the {Ø} node (uncovered branch)
            target = current_sequence + [ConditionResult(node.condition, True)]
            return target

        # Skip covered leaf nodes and infeasible leaf nodes (X)
        if not self._is_infeasible_leaf(node.right) and not node.right.is_leaf:
            # If the right child is an internal node, continue recursion
            new_sequence = current_sequence + [ConditionResult(node.condition, True)]
            result = self._check_recursive(node.right, new_sequence)
            if result is not None:
                return result

        # If both branches are covered leaves (T or X), return None
        return None

    def construct_path_constraint(self, sequence: List[ConditionResult]) -> str:
        """
        Constructs the complete Path Constraint (PC) logic expression for the target condition sequence P_target.
        PC is the conjunction (AND) of all conditions and their results along the path, using the
        WP-derived input constraints.

        :param sequence: The target condition sequence (List[ConditionResult])
        :return: The logical expression string to be solved.
        """
        constraints = []

        for cr in sequence:
            # Extract the WP-derived constraint (expression based on initial input variables I)
            predicate = cr.condition.input_constraint.strip()
            if predicate.startswith("(") and predicate.endswith(")"):
                print(f"predicate is {predicate}")
                predicate = predicate[1:len(predicate) - 1]
                print(f"predicate is {predicate}")

            # Construct the constraint based on the expected result (True/False)
            if cr.result:
                # Result is True: constraint is P itself
                constraints.append(f"({predicate})")
            else:
                # Result is False: constraint is NOT P
                # Using '!' for NOT and '&&' for AND for symbolic solver readability
                constraints.append(f"(!({predicate}))")

        if not constraints:
            return "True (Empty Path Constraint)"

        # Conjoin all constraints with ' && '
        full_constraint = " && ".join(constraints)
        return full_constraint

    # -----------------------------------------------------------------
    # 5. Helper Methods (For Display and Debugging)
    # -----------------------------------------------------------------

    def print_tree(self):
        """Prints the structure of the CCT."""
        print("\n--- Current CCT Structure ---")
        self._print_recursive(self.root, 0)
        print("-----------------------------\n")

    def _print_recursive(self, node: Optional[Node], level: int):
        if node is None:
            # Corresponds to the empty set symbol (Ø) in the paper
            print("  |   " * level + "{Ø}")
            return

        indent = "  |   " * level

        if node.is_leaf:
            if node.test_cases == {INFEASIBLE_MARKER}:
                print(indent + "[LEAF] Infeasible (X)")
            else:
                print(indent + f"[LEAF] Cases: {node.test_cases}")
        else:
            # Print condition details
            print(indent + f"[NODE] Condition: {node.condition.condition_string} @L{node.condition.line_number} (WP: {node.condition.input_constraint}, Cnt: {node.condition.loop_count})")

            # Print F (False) branch
            print("  |   " * (level) + "  (F) ->")
            self._print_recursive(node.left, level + 1)

            # Print T (True) branch
            print("  |   " * (level) + "  (T) ->")
            self._print_recursive(node.right, level + 1)







if __name__ == "__main__":
    # test_main()
    # test_main2()
    # test_parse_result()
    # test_keep_throwing_tcs_until_no_more()
    # test_random()
    main()
