-- Retain document structure while removing inline visual formatting.
local function content(element) return element.content end
return {{
  Emph = content, Strong = content, Strikeout = content, SmallCaps = content,
  Superscript = content, Subscript = content, Span = content,
  Div = function(element) element.attr = pandoc.Attr(); return element end
}}
